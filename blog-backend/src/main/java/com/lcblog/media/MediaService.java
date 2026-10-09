package com.lcblog.media;

import static com.lcblog.common.Web.badRequest;

import com.lcblog.common.Web;
import com.lcblog.common.Web.Page;
import com.lcblog.config.BlogProperties;
import com.lcblog.media.OssGateway.ObjectStat;
import com.lcblog.media.OssGateway.PostPolicy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 浏览器直传 OSS 的完整闭环：申请 -> 直传 -> 完成确认（服务端核验） -> READY。
 * MySQL 与 OSS 不在同一事务：删除先标记 DELETE_PENDING，再删对象，最后删记录；
 * 任一步失败都保留记录，由定时任务重试。
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    /** 授权过期后再等待的时间，避免清理掉授权期内开始、仍在传输中的大文件。 */
    static final Duration UPLOAD_GRACE = Duration.ofMinutes(30);
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM");

    public record UploadRequest(String fileName, long size, String visibility, Long articleId) {
    }

    public record UploadTicket(long mediaId, String host, Map<String, String> fields) {
    }

    private final MediaMapper mapper;
    private final OssGateway oss;
    private final BlogProperties props;
    private final TransactionOperations tx;
    private final Clock clock;

    @Autowired
    public MediaService(MediaMapper mapper, OssGateway oss, BlogProperties props, TransactionOperations tx) {
        this(mapper, oss, props, tx, Clock.systemDefaultZone());
    }

    MediaService(MediaMapper mapper, OssGateway oss, BlogProperties props, TransactionOperations tx, Clock clock) {
        this.mapper = mapper;
        this.oss = oss;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
    }

    // ---------- 上传 ----------

    @Transactional
    public UploadTicket requestUpload(UploadRequest req, long adminId) {
        String name = cleanFileName(req.fileName());
        FileType type = FileType.fromFileName(name)
                .orElseThrow(() -> badRequest("不支持的文件类型。允许：图片 jpg/png/gif/webp，文档 pdf/txt/md/office，压缩包 zip/7z/gz/tar/rar"));
        long limit = switch (type.kind) {
            case IMAGE -> props.upload().maxImageBytes();
            case DOCUMENT -> props.upload().maxDocumentBytes();
            case ARCHIVE -> props.upload().maxArchiveBytes();
        };
        if (req.size() <= 0 || req.size() > limit) {
            throw badRequest("文件大小需在 1 字节到 " + limit / 1024 / 1024 + " MB 之间");
        }
        boolean isPublic = MediaFile.PUBLIC.equals(req.visibility());
        if (!isPublic && !MediaFile.PRIVATE.equals(req.visibility())) {
            throw badRequest("visibility 只能是 PUBLIC 或 PRIVATE");
        }
        if (req.articleId() != null && mapper.articleExists(req.articleId()) == 0) {
            throw badRequest("关联的文章不存在");
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.upload().policyTtl());
        MediaFile f = new MediaFile();
        f.originalName = name;
        f.bucket = isPublic ? props.oss().publicBucket() : props.oss().privateBucket();
        // 浏览器只能写入 upload/ 下的暂存 key；完成确认时由后端复制到最终 key
        f.objectKey = newKey("upload/", type.extension, now);
        f.kind = type.kind.name();
        f.contentType = type.contentType;
        f.sizeBytes = req.size();
        f.visibility = isPublic ? MediaFile.PUBLIC : MediaFile.PRIVATE;
        f.status = MediaFile.PENDING;
        f.articleId = req.articleId();
        f.createdBy = adminId;
        f.uploadExpiresAt = LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault());

        // 先签发策略（OSS 未配置时在此失败），再写 PENDING 记录
        PostPolicy policy = oss.postPolicy(f.bucket, f.objectKey, f.contentType, disposition(f), f.sizeBytes, expiresAt);
        mapper.insert(f);
        return new UploadTicket(f.id, policy.host(), policy.fields());
    }

    /**
     * 完成确认：不信任浏览器的“上传成功”，而是到 OSS 查询对象并核验大小与文件头。
     * 只接受记录 ID，bucket / key 一律取自数据库中后端签发的值。
     *
     * <p>上传授权在有效期内可以被重复使用，而 x-oss-forbid-overwrite 在开启版本控制的 Bucket 上不生效，
     * 所以不能指望“禁止覆盖”。做法是把暂存对象复制到一个从未出现在任何授权中的最终 key，
     * 然后核验这个副本：副本只有后端能写，核验结果在之后无论暂存 key 被覆盖多少次都不会改变。
     * 暂存 key 登记为待删除，等授权过期并超过宽限期后再删一次，清掉过期前可能发生的重复上传。
     */
    public MediaFile complete(long mediaId, long adminId) {
        MediaFile f = mapper.findPendingOwnedBy(mediaId, adminId);
        if (f == null) {
            throw Web.notFound();
        }
        String uploadKey = f.objectKey;
        if (oss.stat(f.bucket, uploadKey).isEmpty()) {
            throw badRequest("OSS 中未找到该文件，请确认上传已成功");
        }
        String ext = extensionOf(uploadKey);
        String finalKey = newKey(f.isPublic() ? "public/" : "private/", ext, clock.instant());
        oss.copy(f.bucket, uploadKey, f.bucket, finalKey);

        String problem = verify(f, finalKey, ext);
        if (problem != null) {
            deleteQuietly(f.bucket, finalKey);
            discard(f);
            throw badRequest(problem);
        }
        try {
            tx.executeWithoutResult(s -> {
                if (mapper.markReady(f.id, finalKey) == 0) {
                    throw badRequest("上传记录已失效，请重新上传");
                }
                mapper.insert(deletePendingCopy(f, f.bucket, uploadKey, f.uploadExpiresAt));
            });
        } catch (RuntimeException e) {
            abandon(f.id, f.bucket, finalKey);
            throw e;
        }
        deleteQuietly(f.bucket, uploadKey); // 尽早释放空间；最终删除仍由清理任务在授权过期后完成
        f.objectKey = finalKey;
        f.status = MediaFile.READY;
        f.uploadExpiresAt = null;
        return f;
    }

    /** 核验后端自己复制出的对象；返回 null 表示通过。 */
    private String verify(MediaFile f, String key, String ext) {
        Optional<ObjectStat> stat = oss.stat(f.bucket, key);
        if (stat.isEmpty() || stat.get().size() != f.sizeBytes) {
            return "文件大小与申请时不一致，已丢弃";
        }
        FileType type = FileType.fromFileName("x." + ext).orElseThrow();
        if (!type.matches(oss.readHead(f.bucket, key, FileType.HEAD_BYTES))) {
            return "文件内容与扩展名不符，已丢弃";
        }
        return null;
    }

    // ---------- 访问 ----------

    /** 访客入口：只对 READY + PUBLIC（且所属文章已发布）的文件给出地址。 */
    public Optional<String> publicUrl(long id) {
        MediaFile f = mapper.findPublicReady(id);
        if (f == null) {
            return Optional.empty();
        }
        return Optional.of(oss.publicUrl(f.objectKey).orElseGet(() -> presign(f)));
    }

    /** 管理员入口：调用方已通过 Spring Security 校验管理员身份。草稿图片在编辑器预览中也走这里。 */
    public String adminUrl(long id) {
        MediaFile f = mapper.findById(id);
        if (f == null || !MediaFile.READY.equals(f.status)) {
            throw Web.notFound();
        }
        return presign(f);
    }

    public List<MediaFile> attachmentsOf(long publishedArticleId) {
        return mapper.listPublicReadyByPublishedArticle(publishedArticleId);
    }

    public List<MediaFile> publicImages() {
        return mapper.listPublicReadyImages();
    }

    public Page<MediaFile> list(int page) {
        return Page.of(mapper.listAll(Web.offset(page), Web.PAGE_SIZE), page, mapper.countAll());
    }

    // ---------- 管理 ----------

    @Transactional
    public void link(long id, Long articleId) {
        if (articleId != null && mapper.articleExists(articleId) == 0) {
            throw badRequest("文章不存在");
        }
        if (mapper.linkArticle(id, articleId) == 0) {
            throw badRequest("只能关联已上传完成的文件");
        }
    }

    /**
     * 公开/私有位于不同 Bucket：复制到目标 Bucket 的全新 key（不复用旧路径，旧记录仍在 DELETE_PENDING 时
     * 立即切回也不会撞唯一约束），再在一个事务内让记录指向新对象并把旧对象登记为待删除。
     * 复制或数据库失败时记录仍指向原对象，原文件始终可用；只撤销本次复制出的新对象。
     */
    public void changeVisibility(long id, String visibility) {
        MediaFile f = mapper.findById(id);
        if (f == null || !MediaFile.READY.equals(f.status)) {
            throw badRequest("只能修改已上传完成的文件");
        }
        boolean toPublic = MediaFile.PUBLIC.equals(visibility);
        if (!toPublic && !MediaFile.PRIVATE.equals(visibility)) {
            throw badRequest("visibility 只能是 PUBLIC 或 PRIVATE");
        }
        if (visibility.equals(f.visibility)) {
            return;
        }
        String bucket = toPublic ? props.oss().publicBucket() : props.oss().privateBucket();
        String key = newKey(toPublic ? "public/" : "private/", extensionOf(f.objectKey), clock.instant());
        oss.copy(f.bucket, f.objectKey, bucket, key);
        MediaFile old = deletePendingCopy(f, f.bucket, f.objectKey, null);
        try {
            tx.executeWithoutResult(s -> {
                if (mapper.relocate(f.id, f.objectKey, bucket, key, visibility) == 0) {
                    throw badRequest("文件状态已变化，请刷新后重试");
                }
                mapper.insert(old);
            });
        } catch (RuntimeException e) {
            abandon(f.id, bucket, key);
            throw e;
        }
        purge(old); // 旧对象已不被任何 READY 记录使用；失败则由清理任务重试
    }

    /** 删除文件：仍被文章使用（正文、封面、附件关联）或被相册引用时拒绝。 */
    public void delete(long id) {
        MediaFile f = mapper.findById(id);
        if (f == null || MediaFile.DELETE_PENDING.equals(f.status)) {
            throw Web.notFound();
        }
        int refs = mapper.countReferences(id);
        if (refs > 0) {
            throw badRequest("该文件仍被 " + refs + " 处文章正文、封面、附件关联或相册使用，请先移除");
        }
        if (mapper.markDeletePending(id, f.objectKey) == 0) {
            throw badRequest("文件状态已变化，请刷新后重试");
        }
        if (MediaFile.PENDING.equals(f.status)) {
            deleteQuietly(f.bucket, f.objectKey); // 暂存 key 仍可能被写入，记录留给清理任务在授权过期后删除
        } else {
            purge(f);
        }
    }

    // ---------- 清理 ----------

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                clock.instant().minus(UPLOAD_GRACE), ZoneId.systemDefault());
        int expired = mapper.expirePending(cutoff);
        if (expired > 0) {
            log.info("{} 个过期未确认的上传已转为待删除", expired);
        }
        mapper.listDeletePending(cutoff, 100).forEach(this::purge);
    }

    /** 核验失败的上传：标记待删除，暂存 key 的最终删除等授权过期后由清理任务完成。 */
    private void discard(MediaFile f) {
        mapper.markDeletePending(f.id, f.objectKey);
        deleteQuietly(f.bucket, f.objectKey);
    }

    /**
     * 撤销一次失败操作中复制出的新对象。先确认记录没有指向它（例如提交结果未知时其实已经提交），
     * 确认不了就宁可留下孤立对象，也不冒险删除当前有效的文件。
     */
    private void abandon(long id, String bucket, String key) {
        try {
            MediaFile now = mapper.findById(id);
            if (now != null && key.equals(now.objectKey)) {
                return;
            }
        } catch (RuntimeException e) {
            log.warn("无法确认文件 {} 的当前状态，保留新复制的对象", id);
            return;
        }
        deleteQuietly(bucket, key);
    }

    private void deleteQuietly(String bucket, String key) {
        try {
            oss.delete(bucket, key);
        } catch (RuntimeException e) {
            log.warn("删除 OSS 对象失败，稍后由清理任务处理: {}", e.getClass().getSimpleName());
        }
    }

    /** 先删 OSS 对象再删记录；失败保留 DELETE_PENDING 记录供下次重试。日志不含 key 以外的敏感信息。 */
    private void purge(MediaFile f) {
        try {
            oss.delete(f.bucket, f.objectKey);
            mapper.deleteRow(f.id);
        } catch (RuntimeException e) {
            log.warn("删除文件 {} 失败，将在下次清理时重试: {}", f.id, e.getClass().getSimpleName());
        }
    }

    /** 一条只用于清理指定对象的 DELETE_PENDING 记录。 */
    private static MediaFile deletePendingCopy(MediaFile f, String bucket, String key, LocalDateTime notBefore) {
        MediaFile d = new MediaFile();
        d.originalName = f.originalName;
        d.bucket = bucket;
        d.objectKey = key;
        d.kind = f.kind;
        d.contentType = f.contentType;
        d.sizeBytes = f.sizeBytes;
        d.visibility = f.visibility;
        d.status = MediaFile.DELETE_PENDING;
        d.uploadExpiresAt = notBefore;
        return d;
    }

    /** 不可预测的 key：随机 UUID，不含原始文件名；每次复制都用新 key，绝不复用。 */
    private static String newKey(String prefix, String ext, Instant now) {
        return prefix + KEY_DATE.format(now.atZone(ZoneId.systemDefault())) + "/" + UUID.randomUUID() + "." + ext;
    }

    private String presign(MediaFile f) {
        return oss.presignGet(f.bucket, f.objectKey, props.upload().downloadUrlTtl(), f.contentType, disposition(f));
    }

    /** 图片内联展示，其余一律作为附件下载并保留原始文件名。 */
    static String disposition(MediaFile f) {
        ContentDisposition.Builder b = f.isImage() ? ContentDisposition.inline() : ContentDisposition.attachment();
        return b.filename(f.originalName, StandardCharsets.UTF_8).build().toString();
    }

    static String cleanFileName(String raw) {
        if (raw == null) {
            throw badRequest("文件名不能为空");
        }
        String name = raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1)
                .replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty() || name.length() > 200) {
            throw badRequest("文件名为空或过长（最多 200 字）");
        }
        return name;
    }

    private static String extensionOf(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }
}
