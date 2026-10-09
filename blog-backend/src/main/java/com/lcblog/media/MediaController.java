package com.lcblog.media;

import com.lcblog.auth.AdminUser;
import com.lcblog.auth.AdminUserMapper;
import com.lcblog.common.Result;
import com.lcblog.common.Web;
import com.lcblog.common.Web.Page;
import com.lcblog.media.MediaService.UploadRequest;
import com.lcblog.media.MediaService.UploadTicket;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MediaController {

    record LinkRequest(Long articleId) {
    }

    record VisibilityRequest(String visibility) {
    }

    private final MediaService media;
    private final AdminUserMapper admins;

    MediaController(MediaService media, AdminUserMapper admins) {
        this.media = media;
        this.admins = admins;
    }

    /**
     * 稳定资源入口 /api/media/{id}：访客只能拿到已就绪、公开、且所属文章已发布的文件；
     * 已登录的管理员拿到任意就绪文件的短时签名地址，因此能在后台预览草稿图片。
     */
    @GetMapping("/api/media/{id}")
    ResponseEntity<Void> publicFile(@PathVariable long id, HttpServletRequest request) {
        if (request.isUserInRole("ADMIN")) {
            return redirect(media.adminUrl(id));
        }
        return redirect(media.publicUrl(id).orElseThrow(Web::notFound));
    }

    // ---------- 管理员（/api/admin/** 已由 Spring Security 限定） ----------

    @GetMapping("/api/admin/media")
    Result<Page<MediaFile>> list(@RequestParam(required = false) Integer page) {
        return Result.ok(media.list(Web.page(page)));
    }

    /** 可作为封面的公开图片。 */
    @GetMapping("/api/admin/media/images")
    Result<List<MediaFile>> images() {
        return Result.ok(media.publicImages());
    }

    @GetMapping("/api/admin/media/{id}/download")
    ResponseEntity<Void> adminFile(@PathVariable long id) {
        return redirect(media.adminUrl(id));
    }

    @PostMapping("/api/admin/media/uploads")
    Result<UploadTicket> requestUpload(@RequestBody UploadRequest req, Principal principal) {
        return Result.ok(media.requestUpload(req, adminId(principal)));
    }

    @PostMapping("/api/admin/media/uploads/{id}/complete")
    Result<Map<String, Object>> complete(@PathVariable long id, Principal principal) {
        MediaFile f = media.complete(id, adminId(principal));
        return Result.ok(Map.of("id", f.id, "markdown", f.markdown(), "public", f.isPublic(),
                "url", MediaFile.url(f.id)));
    }

    @PutMapping("/api/admin/media/{id}/article")
    Result<Void> link(@PathVariable long id, @RequestBody LinkRequest req) {
        media.link(id, req.articleId());
        return Result.ok();
    }

    @PutMapping("/api/admin/media/{id}/visibility")
    Result<Void> visibility(@PathVariable long id, @RequestBody VisibilityRequest req) {
        media.changeVisibility(id, req.visibility());
        return Result.ok();
    }

    @DeleteMapping("/api/admin/media/{id}")
    Result<Void> delete(@PathVariable long id) {
        media.delete(id);
        return Result.ok();
    }

    private static ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url))
                .header("Cache-Control", "no-store").build();
    }

    private long adminId(Principal principal) {
        AdminUser user = principal == null ? null : admins.findByUsername(principal.getName());
        if (user == null) {
            throw Web.notFound();
        }
        return user.id;
    }
}
