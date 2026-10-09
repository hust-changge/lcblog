package com.lcblog.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lcblog.article.ArticleForm;
import com.lcblog.article.ArticleService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * 真实 MySQL + Flyway + 完整 Spring 上下文（安全链、Session、CSRF、MyBatis、事务、外键），
 * 只把 OSS 换成内存假实现。需要一个专用的空库，未提供时跳过：
 * <pre>mvn test -Dlcblog.it.db-url=jdbc:mysql://127.0.0.1:3306/lcblog_it -Dlcblog.it.db-user=lcblog -Dlcblog.it.db-password=...</pre>
 * 每个测试开始前清空文章与文件相关表，请勿指向有数据的库。
 */
@SpringBootTest(properties = {
        "BLOG_ADMIN_USERNAME=it-admin",
        "BLOG_ADMIN_PASSWORD=it-admin-password-123",
        "blog.oss.endpoint=oss-cn-test.aliyuncs.com",
        "blog.oss.public-bucket=pub-bucket",
        "blog.oss.private-bucket=priv-bucket"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "lcblog.it.db-url", matches = ".+")
class MediaLifecycleMySqlTest {

    static final byte[] PNG = MediaServiceTest.PNG;

    @TestConfiguration
    static class FakeOssConfig {
        @Bean
        @Primary
        MediaServiceTest.FakeOss fakeOss() {
            return new MediaServiceTest.FakeOss();
        }
    }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getProperty("lcblog.it.db-url"));
        r.add("spring.datasource.username", () -> System.getProperty("lcblog.it.db-user", "lcblog"));
        r.add("spring.datasource.password", () -> System.getProperty("lcblog.it.db-password", ""));
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MediaServiceTest.FakeOss oss;
    @Autowired
    MediaService media;
    @Autowired
    ArticleService articles;

    MockHttpSession admin;

    @BeforeEach
    void reset() throws Exception {
        jdbc.update("UPDATE article SET cover_media_id = NULL");
        jdbc.update("DELETE FROM album_photo");
        jdbc.update("DELETE FROM album");
        jdbc.update("DELETE FROM media_file");
        jdbc.update("DELETE FROM article_tag");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM tag");
        oss.objects.clear();
        admin = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "it-admin").param("password", "it-admin-password-123"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession();
    }

    // ---------- 辅助：通过真实接口操作 ----------

    private long createDraft(String slug, String content) throws Exception {
        String body = mvc.perform(post("/api/admin/articles").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"slug\":\"" + slug + "\",\"contentMd\":\"" + content + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 申请上传 -> 按授权写入假 OSS -> 完成确认；返回文件 ID 和浏览器拿到的上传 key。 */
    private Map.Entry<Long, String> upload(String visibility, Long articleId) throws Exception {
        String body = mvc.perform(post("/api/admin/media/uploads").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"a.png\",\"size\":" + PNG.length + ",\"visibility\":\"" + visibility
                                + "\",\"articleId\":" + articleId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.mediaId")).longValue();
        String key = JsonPath.read(body, "$.data.fields.key");
        String bucket = "PUBLIC".equals(visibility) ? "pub-bucket" : "priv-bucket";
        oss.put(bucket, key, PNG);
        mvc.perform(post("/api/admin/media/uploads/" + id + "/complete").session(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markdown").value("![a.png](/api/media/" + id + ")"));
        return Map.entry(id, key);
    }

    private int anonymousStatus(long mediaId) throws Exception {
        return mvc.perform(get("/api/media/" + mediaId)).andReturn().getResponse().getStatus();
    }

    private String row(long id, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM media_file WHERE id = ?", String.class, id);
    }

    private boolean hidden(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT hidden FROM media_file WHERE id = ?", Boolean.class, id));
    }

    // ---------- 测试 ----------

    @Test
    void galleryHidesDraftPhotosAndCoversUntilPublished() throws Exception {
        long article = createDraft("gallery-draft", "x");
        long img = upload("PUBLIC", article).getKey();
        jdbc.update("INSERT INTO album (name) VALUES ('Gallery')");
        long album = jdbc.queryForObject("SELECT MAX(id) FROM album", Long.class);
        jdbc.update("INSERT INTO album_photo (album_id, media_id, title) VALUES (?, ?, 'Draft photo')", album, img);

        mvc.perform(get("/api/public/gallery").param("parentId", Long.toString(album)))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").isEmpty());
        mvc.perform(get("/api/public/gallery"))
                .andExpect(jsonPath("$.data.page[0].url").isEmpty());

        articles.publish(article);
        mvc.perform(get("/api/public/gallery").param("parentId", Long.toString(album)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page[0].mediaId").value(img));
        mvc.perform(get("/api/public/gallery"))
                .andExpect(jsonPath("$.data.page[0].url").value("/api/media/" + img));

        mvc.perform(post("/api/admin/articles/" + article + "/unpublish").session(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/gallery").param("parentId", Long.toString(album)))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /** 登录 -> 草稿 -> 上传图片 -> 发布 / 取消发布 -> 管理员预览 -> 删除草稿：访客可见性随文章状态变化。 */
    @Test
    void draftImageFollowsArticleLifecycle() throws Exception {
        long article = createDraft("draft-a", "x");
        long img = upload("PUBLIC", article).getKey();

        assertThat(anonymousStatus(img)).as("草稿图片").isEqualTo(404);
        mvc.perform(get("/api/media/" + img).session(admin)).andExpect(status().isFound()); // 管理员可预览

        mvc.perform(post("/api/admin/articles/" + article + "/publish").session(admin).with(csrf())).andExpect(status().isOk());
        assertThat(anonymousStatus(img)).as("已发布").isEqualTo(302);
        mvc.perform(get("/api/public/articles/draft-a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachments[0].id").value(img));

        mvc.perform(post("/api/admin/articles/" + article + "/unpublish").session(admin).with(csrf())).andExpect(status().isOk());
        assertThat(anonymousStatus(img)).as("取消发布").isEqualTo(404);

        // 删除草稿：外键把 article_id 置空，但文件仍对访客隐藏，文件本体保留
        mvc.perform(delete("/api/admin/articles/" + article).session(admin).with(csrf())).andExpect(status().isOk());
        assertThat(row(img, "article_id")).isNull();
        assertThat(hidden(img)).isTrue();
        assertThat(anonymousStatus(img)).as("删除草稿后").isEqualTo(404);
        assertThat(media.publicUrl(img)).isEmpty();
        assertThat(oss.objects).containsKey("pub-bucket/" + row(img, "object_key"));

        // 管理员明确设为独立素材后才公开
        mvc.perform(put("/api/admin/media/" + img + "/article").session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"articleId\":null}")).andExpect(status().isOk());
        assertThat(anonymousStatus(img)).isEqualTo(302);
    }

    @Test
    void deletingPublishedArticleKeepsItsAttachmentsPublic() throws Exception {
        long article = createDraft("pub-a", "x");
        long img = upload("PUBLIC", article).getKey();
        articles.publish(article);
        articles.delete(article);
        assertThat(hidden(img)).isFalse();
        assertThat(anonymousStatus(img)).isEqualTo(302);
    }

    /** 核验后再次使用原上传授权覆盖暂存 key，READY 文件不受影响；暂存 key 在授权过期后被清理。 */
    @Test
    void reuploadAfterCompleteCannotReplaceReadyFile() throws Exception {
        var up = upload("PUBLIC", null);
        long id = up.getKey();
        String readyKey = row(id, "object_key");
        assertThat(readyKey).startsWith("public/").isNotEqualTo(up.getValue());

        oss.put("pub-bucket", up.getValue(), "<script>evil</script>".getBytes()); // 重复使用授权
        assertThat(oss.objects.get("pub-bucket/" + readyKey)).isEqualTo(PNG);

        media.cleanup(); // 授权未过期：暂存记录保留
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_file WHERE object_key = ?", Integer.class,
                up.getValue())).isEqualTo(1);
        jdbc.update("UPDATE media_file SET upload_expires_at = '2000-01-01' WHERE object_key = ?", up.getValue());
        media.cleanup();
        assertThat(oss.objects).doesNotContainKey("pub-bucket/" + up.getValue()).containsKey("pub-bucket/" + readyKey);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_file", Integer.class)).isEqualTo(1);
    }

    /** PUBLIC -> PRIVATE -> PUBLIC 在清理任务运行前连续执行，不撞唯一约束，清理只删除旧对象。 */
    @Test
    void visibilityCanToggleRepeatedlyBeforeCleanup() throws Exception {
        long id = upload("PUBLIC", null).getKey();
        String k0 = row(id, "object_key");
        for (String v : new String[] {"PRIVATE", "PUBLIC", "PRIVATE", "PUBLIC"}) {
            mvc.perform(put("/api/admin/media/" + id + "/visibility").session(admin).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"" + v + "\"}"))
                    .andExpect(status().isOk());
            assertThat(row(id, "visibility")).isEqualTo(v);
        }
        String current = row(id, "object_key");
        assertThat(current).startsWith("public/").isNotEqualTo(k0);

        // 模拟旧对象的即时删除失败、上传暂存记录也已过期：清理任务只能删掉不再被使用的对象
        jdbc.update("UPDATE media_file SET status = 'DELETE_PENDING', upload_expires_at = '2000-01-01' WHERE id <> ?", id);
        media.cleanup();
        assertThat(oss.objects.keySet()).containsExactly("pub-bucket/" + current);
        assertThat(anonymousStatus(id)).isEqualTo(302);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_file", Integer.class)).isEqualTo(1);
    }

    @Test
    void privateAttachmentNeverGetsPublicUrl() throws Exception {
        long article = createDraft("priv-a", "x");
        long doc = upload("PRIVATE", article).getKey();
        articles.publish(article);
        assertThat(anonymousStatus(doc)).isEqualTo(404);
        mvc.perform(get("/api/admin/media/" + doc + "/download")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/media/" + doc + "/download").session(admin)).andExpect(status().isFound());
    }

    /** 删除保护：正文以 /api/media/{id} 引用、或作为附件关联到文章时拒绝删除；/api/media/{id}0 不算引用。 */
    @Test
    void deleteProtectionRecognizesBodyReferencesAndAttachments() throws Exception {
        long img = upload("PUBLIC", null).getKey();
        long other = upload("PUBLIC", null).getKey();
        createDraft("ref-a", "![a](/api/media/" + img + ")");
        assertThatThrownBy(() -> media.delete(img)).isInstanceOf(ResponseStatusException.class);

        long article = createDraft("ref-b", "/api/media/" + other + "0");
        media.link(other, article);
        assertThatThrownBy(() -> media.delete(other)).hasMessageContaining("使用");
        media.link(other, null);
        media.delete(other);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_file WHERE id = ?", Integer.class, other)).isZero();
    }

    /** 文章与标签在一个事务中：文章写入后标签处理失败，整体回滚，文章本身也不会留下。 */
    @Test
    void articleAndTagsRollBackTogether() {
        // "!!!" 无法生成标签地址，在 article 已 INSERT 之后才抛错
        assertThatThrownBy(() -> articles.save(null, new ArticleForm("B", "tx-b", "", "", null, "Java, !!!", false, null)))
                .hasMessageContaining("无法生成地址");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM article WHERE slug = 'tx-b'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tag WHERE name = 'Java'", Integer.class)).isZero();
    }
}
