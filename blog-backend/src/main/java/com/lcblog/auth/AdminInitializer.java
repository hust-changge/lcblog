package com.lcblog.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 一次性初始化唯一管理员：仅当 admin_user 为空且提供了环境变量时创建。
 * 创建成功后应从部署环境中移除 BLOG_ADMIN_PASSWORD。
 */
@Component
class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AdminUserMapper mapper;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    AdminInitializer(AdminUserMapper mapper, PasswordEncoder encoder,
                     @Value("${BLOG_ADMIN_USERNAME:}") String username,
                     @Value("${BLOG_ADMIN_PASSWORD:}") String password) {
        this.mapper = mapper;
        this.encoder = encoder;
        this.username = username.trim();
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (mapper.count() > 0) {
            if (!password.isEmpty()) {
                log.warn("管理员已存在，BLOG_ADMIN_PASSWORD 被忽略，请从环境中移除该变量");
            }
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            log.warn("尚无管理员账号：设置 BLOG_ADMIN_USERNAME 和 BLOG_ADMIN_PASSWORD 后重启以完成初始化");
            return;
        }
        if (!username.matches("[A-Za-z0-9_.-]{3,64}") || password.length() < 12) {
            throw new IllegalStateException("管理员用户名需为 3-64 位字母数字，密码至少 12 位");
        }
        mapper.insert(username, encoder.encode(password));
        log.info("已创建管理员账号 {}", username);
    }
}
