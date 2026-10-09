package com.lcblog.album;

import java.time.LocalDateTime;

/** 相册或照片：type 1 为相册，2 为照片（与 Ruyu-Blog 前台相册页的数据约定一致）。 */
public class AlbumItem {
    public Long id;
    public int type;
    public String name;
    public String description;
    public Long parentId;
    public Long mediaId;
    /** 相册封面（第一张公开照片）或照片本身的站内地址 */
    public String url;
    public LocalDateTime createdAt;
}
