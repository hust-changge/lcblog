package com.lcblog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lcblog.article.Article;
import com.lcblog.article.ArticleMapper;
import com.lcblog.article.ArticleService;
import com.lcblog.article.MarkdownRenderer;
import com.lcblog.article.TaxonomyMapper;
import com.lcblog.auth.AdminUser;
import com.lcblog.auth.AdminUserMapper;
import com.lcblog.common.Web.Page;
import com.lcblog.config.SecurityConfig;
import com.lcblog.media.MediaService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** REST 接口的访问控制与错误格式：真实 SecurityFilterChain + 控制器，业务层用 mock。 */
@WebMvcTest
@Import(SecurityConfig.class)
class ApiSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ArticleService articleService;
    @MockitoBean
    ArticleMapper articleMapper;
    @MockitoBean
    TaxonomyMapper taxonomyMapper;
    @MockitoBean
    MediaService mediaService;
    @MockitoBean
    AdminUserMapper adminUserMapper;
    @MockitoBean
    com.lcblog.album.AlbumMapper albumMapper;
    @TestBean
    MarkdownRenderer markdownRenderer;

    static MarkdownRenderer markdownRenderer() {
        return new MarkdownRenderer();
    }

    // ---------- 未登录访问后台：一律 401 JSON，不重定向 ----------

    @Test
    void anonymousCannotWriteArticlesEvenWithValidCsrf() throws Exception {
        mvc.perform(post("/api/admin/articles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"slug\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/api/admin/articles/1/publish").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/articles/1").with(csrf())).andExpect(status().isUnauthorized());
        verify(articleService, never()).save(any(), any());
        verify(articleService, never()).publish(anyLong());
        verify(articleService, never()).delete(anyLong());
    }

    @Test
    void anonymousCannotRequestOrCompleteUploads() throws Exception {
        mvc.perform(post("/api/admin/media/uploads").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"a.png\",\"size\":1,\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/media/uploads/1/complete").with(csrf()))
                .andExpect(status().isUnauthorized());
        verify(mediaService, never()).requestUpload(any(), anyLong());
        verify(mediaService, never()).complete(anyLong(), anyLong());
    }

    @Test
    void anonymousCannotGetPrivateDownloadOrAdminLists() throws Exception {
        mvc.perform(get("/api/admin/media/5/download")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/articles")).andExpect(status().isUnauthorized());
        verify(mediaService, never()).adminUrl(anyLong());
        verify(articleService, never()).adminList(anyInt());

        when(mediaService.publicUrl(5L)).thenReturn(Optional.empty()); // 私有文件在公开入口不可见
        mvc.perform(get("/api/media/5")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void anonymousCannotManageAlbums() throws Exception {
        mvc.perform(post("/api/admin/albums").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/albums/1/photos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"mediaId\":1}"))
                .andExpect(status().isUnauthorized());
        verify(albumMapper, never()).insertAlbum(any(), any(), any());
        mvc.perform(get("/api/public/gallery")).andExpect(status().isOk());
    }

    @Test
    void unknownPathsAreDeniedByDefault() throws Exception {
        mvc.perform(get("/api/internal/anything")).andExpect(status().isUnauthorized());
    }

    // ---------- 登录 / 会话 / CSRF ----------

    @Test
    void loginReturnsJsonAndNeverRedirects() throws Exception {
        AdminUser admin = new AdminUser();
        admin.id = 1L;
        admin.username = "admin";
        admin.passwordHash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("correct-password");
        when(adminUserMapper.findByUsername("admin")).thenReturn(admin);

        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "admin").param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("用户名或密码错误"))
                .andExpect(header().doesNotExist("Location"));

        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "admin").param("password", "correct-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("admin"));
    }

    @Test
    void loginWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", "admin").param("password", "x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg", containsString("CSRF")));
    }

    @Test
    void meReportsSessionState() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN=")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminWritesRequireCsrfToken() throws Exception {
        mvc.perform(post("/api/admin/articles/1/publish")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(articleService, never()).publish(anyLong());

        mvc.perform(post("/api/admin/articles/1/publish").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(articleService).publish(1L);
        mvc.perform(get("/api/auth/me")).andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void spaFlowCookieTokenEchoedInHeaderIsAccepted() throws Exception {
        // 模拟浏览器：先取 XSRF-TOKEN Cookie，再把原值放进 X-XSRF-TOKEN 请求头（axios 的默认行为）
        jakarta.servlet.http.Cookie token = mvc.perform(get("/api/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        org.assertj.core.api.Assertions.assertThat(token).isNotNull();
        org.assertj.core.api.Assertions.assertThat(token.isHttpOnly()).isFalse();

        mvc.perform(post("/api/admin/articles/2/publish").cookie(token).header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/articles/2/unpublish").cookie(token).header("X-XSRF-TOKEN", "forged"))
                .andExpect(status().isForbidden());
        verify(articleService).publish(2L);
        verify(articleService, never()).unpublish(2L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void validationErrorsAreJson400() throws Exception {
        mvc.perform(post("/api/admin/articles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"slug\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("标题不能为空"));
    }

    // ---------- 公开接口 ----------

    @Test
    void draftSlugIsNotFoundForVisitors() throws Exception {
        when(articleService.published("draft-post")).thenReturn(Optional.empty());
        mvc.perform(get("/api/public/articles/draft-post")).andExpect(status().isNotFound());
    }

    @Test
    void publishedDetailReturnsSanitizedHtmlOnly() throws Exception {
        Article a = new Article();
        a.id = 1L;
        a.title = "JVM";
        a.slug = "jvm";
        a.summary = "";
        a.status = Article.PUBLISHED;
        a.contentMd = "## 概述\n\n<script>alert(1)</script>";
        a.publishedAt = a.updatedAt = LocalDateTime.of(2026, 10, 1, 0, 0);
        when(articleService.published("jvm")).thenReturn(Optional.of(a));
        when(articleMapper.listAllPublished()).thenReturn(List.of(a));

        mvc.perform(get("/api/public/articles/jvm")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toc[0].id").value("概述"))
                .andExpect(jsonPath("$.data.article.contentMd").doesNotExist())
                .andExpect(content().string(not(containsString("<script>"))));
    }

    @Test
    void publicListAndSitemapWork() throws Exception {
        when(articleService.latest(1)).thenReturn(Page.of(List.of(), 1, 0));
        mvc.perform(get("/api/public/articles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/sitemap.xml")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/admin"))));
    }

    @Test
    void publicFileRedirectsToShortLivedUrl() throws Exception {
        when(mediaService.publicUrl(6L)).thenReturn(Optional.of("https://bucket.oss.test/public/a.png?Signature=x"));
        mvc.perform(get("/api/media/6")).andExpect(status().isFound())
                .andExpect(redirectedUrl("https://bucket.oss.test/public/a.png?Signature=x"));
        verify(mediaService, never()).adminUrl(anyLong());
    }

    /** 管理员在编辑器里预览草稿图片：同一个稳定地址，走管理员签名而不是访客可见性检查。 */
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanPreviewDraftImagesThroughStableUrl() throws Exception {
        when(mediaService.adminUrl(7L)).thenReturn("https://bucket.oss.test/public/draft.png?Signature=y");
        mvc.perform(get("/api/media/7")).andExpect(status().isFound())
                .andExpect(redirectedUrl("https://bucket.oss.test/public/draft.png?Signature=y"));
        verify(mediaService, never()).publicUrl(anyLong());
    }
}
