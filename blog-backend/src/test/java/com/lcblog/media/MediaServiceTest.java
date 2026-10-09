package com.lcblog.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lcblog.config.BlogProperties;
import com.lcblog.media.MediaService.UploadRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

/** 用内存假 OSS 验证直传闭环的服务端核验，不依赖真实云服务。 */
class MediaServiceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    /** 内存版 OSS：bucket/key -> 内容。 */
    static class FakeOss implements OssGateway {
        final Map<String, byte[]> objects = new HashMap<>();
        int presigned;

        void put(String bucket, String key, byte[] data) {
            objects.put(bucket + "/" + key, data);
        }

        @Override
        public PostPolicy postPolicy(String bucket, String key, String ct, String cd, long size, Instant exp) {
            return new PostPolicy("https://" + bucket + ".oss.test", Map.of("key", key));
        }

        @Override
        public Optional<ObjectStat> stat(String bucket, String key) {
            byte[] d = objects.get(bucket + "/" + key);
            return d == null ? Optional.empty() : Optional.of(new ObjectStat(d.length, "x"));
        }

        @Override
        public byte[] readHead(String bucket, String key, int bytes) {
            byte[] d = objects.get(bucket + "/" + key);
            return java.util.Arrays.copyOf(d, Math.min(bytes, d.length));
        }

        @Override
        public String presignGet(String bucket, String key, Duration ttl, String ct, String cd) {
            presigned++;
            return "https://signed/" + key;
        }

        @Override
        public Optional<String> publicUrl(String key) {
            return Optional.empty();
        }

        @Override
        public void copy(String sb, String sk, String db, String dk) {
            objects.put(db + "/" + dk, objects.get(sb + "/" + sk));
        }

        @Override
        public void delete(String bucket, String key) {
            objects.remove(bucket + "/" + key);
        }
    }

    private MediaMapper mapper;
    private FakeOss oss;
    private MediaService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MediaMapper.class);
        oss = new FakeOss();
        BlogProperties props = new BlogProperties(
                new BlogProperties.Site("t", "d", "http://localhost"),
                new BlogProperties.Profile("n", "i", "r", List.of(), List.of(), "", "", "", "", "2026-01-01"),
                new BlogProperties.Oss("oss-cn-test.aliyuncs.com", "cn-test", "pub-bucket", "priv-bucket", "", ""),
                new BlogProperties.Upload(Duration.ofMinutes(10), Duration.ofMinutes(5), 10 << 20, 50 << 20, 100 << 20));
        service = new MediaService(mapper, oss, props, TransactionOperations.withoutTransaction(),
                Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneId.of("UTC")));
    }

    static final String UPLOAD_KEY = "upload/2026/10/k.png";

    private MediaFile pending(String key, long size) {
        MediaFile f = new MediaFile();
        f.id = 5L;
        f.originalName = "a.png";
        f.bucket = "pub-bucket";
        f.objectKey = key;
        f.kind = "IMAGE";
        f.sizeBytes = size;
        f.visibility = MediaFile.PUBLIC;
        f.status = MediaFile.PENDING;
        f.createdBy = 1L;
        f.uploadExpiresAt = LocalDateTime.of(2026, 10, 8, 0, 10);
        return f;
    }

    private MediaFile ready(String bucket, String key) {
        MediaFile f = pending(key, PNG.length);
        f.bucket = bucket;
        f.status = MediaFile.READY;
        f.uploadExpiresAt = null;
        return f;
    }

    private static HttpStatus status(Throwable e) {
        return HttpStatus.valueOf(((ResponseStatusException) e).getStatusCode().value());
    }

    // ---------- 申请上传 ----------

    @Test
    void requestCreatesPendingRecordWithUnpredictablePrivateKey() {
        doAnswer(inv -> {
            ((MediaFile) inv.getArgument(0)).id = 9L;
            return null;
        }).when(mapper).insert(any());

        var ticket = service.requestUpload(new UploadRequest("..\\..\\论文.pdf", 1000, "PRIVATE", null), 1L);

        assertThat(ticket.mediaId()).isEqualTo(9L);
        verify(mapper).insert(org.mockito.ArgumentMatchers.argThat(f ->
                f.bucket.equals("priv-bucket") && f.objectKey.matches("upload/2026/10/[0-9a-f-]{36}\\.pdf")
                        && f.status.equals(MediaFile.PENDING) && f.originalName.equals("论文.pdf")
                        && f.contentType.equals("application/pdf") && f.createdBy == 1L));
    }

    @Test
    void requestRejectsExecutableWebContentAndOversizedFiles() {
        for (String name : List.of("x.html", "x.svg", "x.js", "x.exe", "noext")) {
            assertThatThrownBy(() -> service.requestUpload(new UploadRequest(name, 10, "PUBLIC", null), 1L))
                    .as(name).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> service.requestUpload(new UploadRequest("big.png", (10 << 20) + 1, "PUBLIC", null), 1L))
                .hasMessageContaining("10 MB");
        assertThatThrownBy(() -> service.requestUpload(new UploadRequest("a.png", 10, "EVERYONE", null), 1L))
                .isInstanceOf(ResponseStatusException.class);
        verify(mapper, never()).insert(any());
    }

    // ---------- 完成确认 ----------

    @Test
    void completeRejectsKeysNotIssuedToCurrentAdmin() {
        when(mapper.findPendingOwnedBy(5L, 2L)).thenReturn(null);
        assertThatThrownBy(() -> service.complete(5L, 2L))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(oss.objects).isEmpty();
        verify(mapper, never()).markReady(anyLong(), anyString());
    }

    @Test
    void completeRejectsMissingObjectAndKeepsPendingForRetry() {
        when(mapper.findPendingOwnedBy(5L, 1L)).thenReturn(pending(UPLOAD_KEY, PNG.length));
        assertThatThrownBy(() -> service.complete(5L, 1L)).hasMessageContaining("未找到");
        verify(mapper, never()).markReady(anyLong(), anyString());
        verify(mapper, never()).markDeletePending(anyLong(), anyString());
    }

    @Test
    void completeRejectsSizeMismatchAndDiscardsObject() {
        when(mapper.findPendingOwnedBy(5L, 1L)).thenReturn(pending(UPLOAD_KEY, 999));
        oss.put("pub-bucket", UPLOAD_KEY, PNG);

        assertThatThrownBy(() -> service.complete(5L, 1L)).hasMessageContaining("大小");
        verify(mapper, never()).markReady(anyLong(), anyString());
        verify(mapper).markDeletePending(5L, UPLOAD_KEY);
        assertThat(oss.objects).isEmpty(); // 暂存对象与后端副本都已删除
    }

    @Test
    void completeRejectsContentThatDoesNotMatchExtension() {
        byte[] html = "<html><script>alert(1)</script>".getBytes();
        when(mapper.findPendingOwnedBy(5L, 1L)).thenReturn(pending(UPLOAD_KEY, html.length));
        oss.put("pub-bucket", UPLOAD_KEY, html);

        assertThatThrownBy(() -> service.complete(5L, 1L)).hasMessageContaining("扩展名");
        verify(mapper, never()).markReady(anyLong(), anyString());
        assertThat(oss.objects).isEmpty();
    }

    @Test
    void completeMarksVerifiedUploadReady() {
        when(mapper.findPendingOwnedBy(5L, 1L)).thenReturn(pending(UPLOAD_KEY, PNG.length));
        when(mapper.markReady(eq(5L), anyString())).thenReturn(1);
        oss.put("pub-bucket", UPLOAD_KEY, PNG);

        MediaFile f = service.complete(5L, 1L);

        assertThat(f.status).isEqualTo(MediaFile.READY);
        assertThat(f.markdown()).isEqualTo("![a.png](/api/media/5)");
    }

    /**
     * 安全边界：上传授权在有效期内可以重复使用（版本控制下 x-oss-forbid-overwrite 也不生效）。
     * 核验通过后再向授权 key 上传任何内容，都不能改变 READY 记录指向的对象。
     */
    @Test
    void reusingUploadPolicyAfterCompleteCannotReplaceVerifiedFile() {
        MediaFile pending = pending(UPLOAD_KEY, PNG.length);
        LocalDateTime policyExpiry = pending.uploadExpiresAt;
        when(mapper.findPendingOwnedBy(5L, 1L)).thenReturn(pending);
        when(mapper.markReady(eq(5L), anyString())).thenReturn(1);
        oss.put("pub-bucket", UPLOAD_KEY, PNG);

        MediaFile f = service.complete(5L, 1L);

        ArgumentCaptor<String> finalKey = ArgumentCaptor.forClass(String.class);
        verify(mapper).markReady(eq(5L), finalKey.capture());
        assertThat(finalKey.getValue()).isEqualTo(f.objectKey).startsWith("public/").isNotEqualTo(UPLOAD_KEY);

        // 攻击者再次使用同一授权（同一 key、同样大小）上传恶意内容
        byte[] evil = Arrays.copyOf("<script>x</script>".getBytes(), PNG.length);
        oss.put("pub-bucket", UPLOAD_KEY, evil);

        assertThat(oss.objects.get("pub-bucket/" + f.objectKey)).isEqualTo(PNG);
        // 暂存 key 登记为待删除，并且要等授权过期之后才最终清理
        verify(mapper).insert(argThat(d -> MediaFile.DELETE_PENDING.equals(d.status)
                && UPLOAD_KEY.equals(d.objectKey) && policyExpiry.equals(d.uploadExpiresAt)));
    }

    @Test
    void completeUndoesFinalCopyWhenDatabaseUpdateFails() {
        when(mapper.findPendingOwnedBy(5L, 1L)).thenReturn(pending(UPLOAD_KEY, PNG.length));
        when(mapper.markReady(eq(5L), anyString())).thenReturn(0); // 例如同时被删除
        when(mapper.findById(5L)).thenReturn(null);
        oss.put("pub-bucket", UPLOAD_KEY, PNG);

        assertThatThrownBy(() -> service.complete(5L, 1L)).hasMessageContaining("失效");
        assertThat(oss.objects.keySet()).containsExactly("pub-bucket/" + UPLOAD_KEY);
    }

    // ---------- 公开/私有切换 ----------

    @Test
    void visibilityChangeUsesFreshKeyAndKeepsOriginalWhenDatabaseFails() {
        String original = "public/2026/10/orig.png";
        when(mapper.findById(5L)).thenReturn(ready("pub-bucket", original));
        oss.put("pub-bucket", original, PNG);
        when(mapper.relocate(eq(5L), eq(original), eq("priv-bucket"), anyString(), eq("PRIVATE")))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.changeVisibility(5L, "PRIVATE")).hasMessage("db down");
        assertThat(oss.objects.keySet()).containsExactly("pub-bucket/" + original);
    }

    @Test
    void concurrentVisibilityChangeLosesWithoutDeletingTheWinnersObject() {
        String original = "public/2026/10/orig.png";
        when(mapper.findById(5L)).thenReturn(ready("pub-bucket", original));
        oss.put("pub-bucket", original, PNG);
        when(mapper.relocate(eq(5L), eq(original), anyString(), anyString(), anyString())).thenReturn(0);

        assertThatThrownBy(() -> service.changeVisibility(5L, "PRIVATE")).hasMessageContaining("刷新");
        assertThat(oss.objects.keySet()).containsExactly("pub-bucket/" + original);
        verify(mapper, never()).insert(any());
    }

    @Test
    void visibilityChangeCopiesToNewKeyThenRetiresOldObject() {
        String original = "public/2026/10/orig.png";
        when(mapper.findById(5L)).thenReturn(ready("pub-bucket", original));
        oss.put("pub-bucket", original, PNG);
        ArgumentCaptor<String> newKey = ArgumentCaptor.forClass(String.class);
        when(mapper.relocate(eq(5L), eq(original), eq("priv-bucket"), newKey.capture(), eq("PRIVATE"))).thenReturn(1);

        service.changeVisibility(5L, "PRIVATE");

        assertThat(newKey.getValue()).startsWith("private/").doesNotContain("orig");
        assertThat(oss.objects.keySet()).containsExactly("priv-bucket/" + newKey.getValue());
        verify(mapper).insert(argThat(d -> MediaFile.DELETE_PENDING.equals(d.status) && original.equals(d.objectKey)
                && d.uploadExpiresAt == null));
    }

    // ---------- 访问 ----------

    @Test
    void privateOrUnreadyFilesGetNoPublicUrl() {
        when(mapper.findPublicReady(5L)).thenReturn(null); // SQL 只返回 READY + PUBLIC
        assertThat(service.publicUrl(5L)).isEmpty();
        assertThat(oss.presigned).isZero();
    }

    @Test
    void deleteRefusesFilesStillReferencedByArticles() {
        when(mapper.findById(5L)).thenReturn(ready("pub-bucket", "public/2026/10/k.png"));
        when(mapper.countReferences(5L)).thenReturn(2);
        assertThatThrownBy(() -> service.delete(5L)).hasMessageContaining("使用");
        verify(mapper, never()).markDeletePending(anyLong(), anyString());
    }

    @Test
    void downloadDispositionKeepsOriginalNameAndForcesAttachmentForNonImages() {
        MediaFile f = pending("private/2026/10/k.pdf", 1);
        f.kind = "DOCUMENT";
        f.originalName = "研究 笔记.pdf";
        assertThat(MediaService.disposition(f)).startsWith("attachment").contains("filename*=UTF-8''");
    }
}
