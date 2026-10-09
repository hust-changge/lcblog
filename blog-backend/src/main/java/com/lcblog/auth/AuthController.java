package com.lcblog.auth;

import com.lcblog.common.Result;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录与退出由 Spring Security 处理（见 SecurityConfig），这里只提供会话状态与 CSRF 令牌。 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    /** SPA 启动时调用一次，确保 XSRF-TOKEN Cookie 已写入。 */
    @GetMapping("/csrf")
    Result<Void> csrf(CsrfToken token) {
        token.getToken(); // 读取即触发延迟加载的令牌写入 Cookie
        return Result.ok();
    }

    /** 当前登录的管理员；未登录返回 401，供后台路由守卫判断。 */
    @GetMapping("/me")
    ResponseEntity<Result<?>> me(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, "未登录"));
        }
        return ResponseEntity.ok(Result.ok(Map.of("username", principal.getName())));
    }
}
