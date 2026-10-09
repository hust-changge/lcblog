package com.lcblog.config;

import com.lcblog.auth.AdminUser;
import com.lcblog.auth.AdminUserMapper;
import com.lcblog.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import tools.jackson.databind.json.JsonMapper;

/**
 * 前后端分离的会话认证：
 * - Session Cookie 保存登录态（HttpOnly，生产环境 Secure）；
 * - CSRF 令牌放在可读 Cookie XSRF-TOKEN 中，SPA 以 X-XSRF-TOKEN 请求头回传；
 * - 所有失败（未登录、无权限、CSRF 失败、登录失败）都返回 JSON 与对应状态码，不重定向。
 */
@Configuration
public class SecurityConfig {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/public/**", "/api/media/**", "/api/auth/**").permitAll()
                        .requestMatchers("/sitemap.xml", "/robots.txt", "/actuator/health").permitAll()
                        // 其他路径（包括未知 /api 路径）一律拒绝，避免新接口忘记配置权限
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.spa())
                .formLogin(form -> form
                        // loginPage 指向接口地址：不生成默认 HTML 登录页，未登录时也不会重定向
                        .loginPage("/api/auth/login")
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((req, res, auth) -> write(res, 200, Result.ok(auth.getName())))
                        .failureHandler((req, res, e) -> write(res, 401, Result.error(401, "用户名或密码错误"))))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((req, res, auth) -> write(res, 200, Result.ok())))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, 401, Result.error(401, "未登录或登录已过期")))
                        .accessDeniedHandler((req, res, ex) -> write(res, 403, Result.error(403,
                                ex instanceof org.springframework.security.web.csrf.CsrfException
                                        ? "CSRF 令牌无效，请刷新页面后重试" : "没有权限"))))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    private static void write(HttpServletResponse res, int status, Result<?> body) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(JSON.writeValueAsString(body));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AdminUserMapper mapper) {
        return username -> {
            AdminUser user = mapper.findByUsername(username);
            if (user == null) {
                throw new UsernameNotFoundException("not found");
            }
            return User.withUsername(user.username).password(user.passwordHash).roles("ADMIN").build();
        };
    }
}
