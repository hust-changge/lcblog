package com.lcblog.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 所有接口错误都以 JSON 返回，状态码与 code 一致，不暴露异常细节。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Result<Void>> status(ResponseStatusException e) {
        int code = e.getStatusCode().value();
        String msg = e.getReason() != null ? e.getReason() : (code == 404 ? "资源不存在" : "请求失败");
        return ResponseEntity.status(code).body(Result.error(code, msg));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Result<Void>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().stream()
                .map(err -> err.getDefaultMessage()).findFirst().orElse("参数不正确");
        return bad(msg);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Result<Void>> unreadable(Exception e) {
        return bad("请求参数格式不正确");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Result<Void>> duplicate(DuplicateKeyException e) {
        return bad("名称或 slug 已存在");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Result<Void>> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(Result.error(404, "资源不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Result<Void>> method(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(Result.error(405, "不支持的请求方法"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Result<Void>> unexpected(Exception e) {
        log.error("未处理的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error(500, "服务器内部错误"));
    }

    private static ResponseEntity<Result<Void>> bad(String msg) {
        return ResponseEntity.badRequest().body(Result.error(400, msg));
    }
}
