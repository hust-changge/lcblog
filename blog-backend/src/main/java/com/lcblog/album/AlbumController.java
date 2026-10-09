package com.lcblog.album;

import com.lcblog.common.Result;
import com.lcblog.common.Web;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 相册接口。访客只能看到 READY + PUBLIC 的图片；管理接口位于 /api/admin/**。 */
@RestController
class AlbumController {

    record AlbumRequest(String name, String description, Long parentId) {
    }

    record PhotoRequest(Long mediaId, String title, String description) {
    }

    private static final int PHOTO_PAGE = 24;

    private final AlbumMapper albums;

    AlbumController(AlbumMapper albums) {
        this.albums = albums;
    }

    /**
     * 某一层的内容：第 1 页先返回子相册，再返回该相册内的照片（根目录没有照片）。
     * 返回结构 {page, total} 与 Ruyu-Blog 前台相册页一致。
     */
    @GetMapping("/api/public/gallery")
    Result<Map<String, Object>> gallery(@RequestParam(required = false) Long parentId,
                                        @RequestParam(required = false) Integer page) {
        int p = Web.page(page);
        List<AlbumItem> items = new ArrayList<>();
        List<AlbumItem> subAlbums = albums.listPublicAlbums(parentId);
        if (p == 1) {
            items.addAll(subAlbums);
        }
        long photos = 0;
        if (parentId != null) {
            photos = albums.countPublicPhotos(parentId);
            items.addAll(albums.listPublicPhotos(parentId, (p - 1) * PHOTO_PAGE, PHOTO_PAGE));
        }
        return Result.ok(Map.of("page", items, "total", subAlbums.size() + photos, "pageSize", PHOTO_PAGE));
    }

    // ---------- 后台 ----------

    @GetMapping("/api/admin/albums")
    Result<List<AlbumItem>> list() {
        return Result.ok(albums.listAlbums());
    }

    @PostMapping("/api/admin/albums")
    Result<Void> create(@RequestBody AlbumRequest req) {
        if (req.parentId() != null && albums.albumExists(req.parentId()) == 0) {
            throw Web.badRequest("上级相册不存在");
        }
        albums.insertAlbum(name(req.name()), description(req.description()), req.parentId());
        return Result.ok();
    }

    @PutMapping("/api/admin/albums/{id}")
    Result<Void> update(@PathVariable long id, @RequestBody AlbumRequest req) {
        if (albums.updateAlbum(id, name(req.name()), description(req.description())) == 0) {
            throw Web.notFound();
        }
        return Result.ok();
    }

    @DeleteMapping("/api/admin/albums/{id}")
    Result<Void> delete(@PathVariable long id) {
        if (albums.deleteAlbum(id) == 0) {
            throw Web.notFound();
        }
        return Result.ok();
    }

    @GetMapping("/api/admin/albums/{id}/photos")
    Result<List<AlbumItem>> photos(@PathVariable long id) {
        return Result.ok(albums.listPhotos(id));
    }

    /** 只能加入已上传完成的公开图片，避免私有文件经由相册暴露。 */
    @PostMapping("/api/admin/albums/{id}/photos")
    Result<Void> addPhoto(@PathVariable long id, @RequestBody PhotoRequest req) {
        if (albums.albumExists(id) == 0) {
            throw Web.notFound();
        }
        if (req.mediaId() == null || albums.countPublicImage(req.mediaId()) == 0) {
            throw Web.badRequest("只能加入已上传完成的公开图片");
        }
        String title = req.title() == null ? "" : req.title().trim();
        if (title.length() > 100) {
            throw Web.badRequest("标题最多 100 字");
        }
        albums.insertPhoto(id, req.mediaId(), title, description(req.description()));
        return Result.ok();
    }

    @DeleteMapping("/api/admin/albums/photos/{photoId}")
    Result<Void> removePhoto(@PathVariable long photoId) {
        if (albums.deletePhoto(photoId) == 0) {
            throw Web.notFound();
        }
        return Result.ok();
    }

    private static String name(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 64) {
            throw Web.badRequest("相册名称需为 1-64 字");
        }
        return n;
    }

    private static String description(String d) {
        String s = d == null ? "" : d.trim();
        if (s.length() > 500) {
            throw Web.badRequest("描述最多 500 字");
        }
        return s;
    }
}
