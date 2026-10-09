package com.lcblog.auth;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper {

    @Select("SELECT id, username, password_hash, created_at FROM admin_user WHERE username = #{username}")
    AdminUser findByUsername(String username);

    @Select("SELECT COUNT(*) FROM admin_user")
    long count();

    @Insert("INSERT INTO admin_user (username, password_hash) VALUES (#{username}, #{passwordHash})")
    void insert(String username, String passwordHash);
}
