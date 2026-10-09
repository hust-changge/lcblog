package com.lcblog.album;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 相册 SQL。名称含 Public 的查询只返回 READY + PUBLIC 的图片。 */
@Mapper
public interface AlbumMapper {

    String PUBLIC_MEDIA = com.lcblog.media.MediaMapper.PUBLIC_READY
            + "AND (m.article_id IS NULL OR EXISTS (SELECT 1 FROM article linked "
            + "WHERE linked.id = m.article_id AND linked.status = 'PUBLISHED')) ";

    @Select("SELECT a.id, 1 AS type, a.name, a.description, a.parent_id, a.created_at, "
            + "(SELECT CONCAT('/api/media/', p.media_id) FROM album_photo p JOIN media_file m ON m.id = p.media_id "
            + "WHERE p.album_id = a.id AND" + PUBLIC_MEDIA + "ORDER BY p.id LIMIT 1) AS url "
            + "FROM album a WHERE a.parent_id <=> #{parentId} ORDER BY a.id")
    List<AlbumItem> listPublicAlbums(Long parentId);

    @Select("SELECT p.id, 2 AS type, p.title AS name, p.description, p.album_id AS parent_id, p.media_id, "
            + "CONCAT('/api/media/', p.media_id) AS url, p.created_at FROM album_photo p JOIN media_file m ON m.id = p.media_id "
            + "WHERE p.album_id = #{albumId} AND" + PUBLIC_MEDIA + "ORDER BY p.id LIMIT #{limit} OFFSET #{offset}")
    List<AlbumItem> listPublicPhotos(long albumId, int offset, int limit);

    @Select("SELECT COUNT(*) FROM album_photo p JOIN media_file m ON m.id = p.media_id WHERE p.album_id = #{albumId} AND"
            + PUBLIC_MEDIA)
    long countPublicPhotos(long albumId);

    // ---------- 后台 ----------

    @Select("SELECT id, 1 AS type, name, description, parent_id, created_at FROM album ORDER BY id")
    List<AlbumItem> listAlbums();

    @Select("SELECT COUNT(*) FROM album WHERE id = #{id}")
    int albumExists(long id);

    @Insert("INSERT INTO album (name, description, parent_id) VALUES (#{name}, #{description}, #{parentId})")
    void insertAlbum(String name, String description, Long parentId);

    @Update("UPDATE album SET name = #{name}, description = #{description} WHERE id = #{id}")
    int updateAlbum(long id, String name, String description);

    /** 子相册与照片记录级联删除；图片文件本身保留在文件库中。 */
    @Delete("DELETE FROM album WHERE id = #{id}")
    int deleteAlbum(long id);

    @Select("SELECT p.id, 2 AS type, p.title AS name, p.description, p.album_id AS parent_id, p.media_id, "
            + "CONCAT('/api/media/', p.media_id) AS url, p.created_at FROM album_photo p WHERE p.album_id = #{albumId} ORDER BY p.id")
    List<AlbumItem> listPhotos(long albumId);

    @Select("SELECT COUNT(*) FROM media_file m WHERE m.id = #{mediaId} AND m.kind = 'IMAGE' AND" + PUBLIC_MEDIA)
    int countPublicImage(long mediaId);

    @Insert("INSERT INTO album_photo (album_id, media_id, title, description) VALUES (#{albumId}, #{mediaId}, #{title}, #{description})")
    void insertPhoto(long albumId, long mediaId, String title, String description);

    @Delete("DELETE FROM album_photo WHERE id = #{id}")
    int deletePhoto(long id);
}
