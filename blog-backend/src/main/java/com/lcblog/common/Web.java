package com.lcblog.common;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 小工具集合：slug 规则、分页、LIKE 转义、业务错误。 */
public final class Web {

    /** 文章与分类 slug：小写字母、数字与连字符，保证地址稳定且无需编码。 */
    public static final Pattern ASCII_SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    /** 标签 slug 允许中文等 Unicode 字母，便于直接由标签名生成。 */
    public static final Pattern TAG_SLUG = Pattern.compile("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*");
    public static final int PAGE_SIZE = 10;

    private Web() {
    }

    public static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public static String slugify(String name) {
        String s = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-");
        return s.replaceAll("^-+|-+$", "");
    }

    /** 转义 LIKE 通配符后包成 %kw%，配合 #{} 参数绑定使用。 */
    public static String likeContains(String keyword) {
        return "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public static int page(Integer page) {
        return page == null || page < 1 ? 1 : Math.min(page, 10_000);
    }

    public static int offset(int page) {
        return (page - 1) * PAGE_SIZE;
    }

    public record Page<T>(List<T> items, int page, int pageSize, long total, int totalPages) {
        public static <T> Page<T> of(List<T> items, int page, long total) {
            return new Page<>(items, page, PAGE_SIZE, total, (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE));
        }

        public boolean hasPrev() {
            return page > 1;
        }

        public boolean hasNext() {
            return page < totalPages;
        }
    }
}
