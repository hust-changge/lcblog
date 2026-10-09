package com.lcblog.auth;

import java.time.LocalDateTime;

public class AdminUser {
    public Long id;
    public String username;
    public String passwordHash;
    public LocalDateTime createdAt;
}
