package com.lcblog.common;

/**
 * 统一 JSON 响应。code 与 HTTP 状态码一致（成功为 200），
 * 与 Ruyu-Blog 前端约定的 {code, msg, data} 结构兼容。
 */
public record Result<T>(int code, String msg, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(200, "ok", null);
    }

    public static Result<Void> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }
}
