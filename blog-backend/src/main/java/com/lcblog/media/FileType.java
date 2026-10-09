package com.lcblog.media;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 允许上传的文件类型白名单。Content-Type 由服务端按扩展名决定（不信任浏览器声明），
 * 完成确认时再用文件头魔数核验真实内容。SVG / HTML 等可执行网页内容不在白名单中。
 */
enum FileType {
    JPG("jpg", Kind.IMAGE, "image/jpeg", h -> starts(h, 0xFF, 0xD8, 0xFF)),
    JPEG("jpeg", Kind.IMAGE, "image/jpeg", h -> starts(h, 0xFF, 0xD8, 0xFF)),
    PNG("png", Kind.IMAGE, "image/png", h -> starts(h, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)),
    GIF("gif", Kind.IMAGE, "image/gif", h -> ascii(h, 0, "GIF87a") || ascii(h, 0, "GIF89a")),
    WEBP("webp", Kind.IMAGE, "image/webp", h -> ascii(h, 0, "RIFF") && ascii(h, 8, "WEBP")),

    PDF("pdf", Kind.DOCUMENT, "application/pdf", h -> ascii(h, 0, "%PDF-")),
    TXT("txt", Kind.DOCUMENT, "text/plain; charset=utf-8", FileType::looksLikeText),
    MD("md", Kind.DOCUMENT, "text/markdown; charset=utf-8", FileType::looksLikeText),
    DOC("doc", Kind.DOCUMENT, "application/msword", FileType::ole),
    XLS("xls", Kind.DOCUMENT, "application/vnd.ms-excel", FileType::ole),
    PPT("ppt", Kind.DOCUMENT, "application/vnd.ms-powerpoint", FileType::ole),
    DOCX("docx", Kind.DOCUMENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", FileType::zip),
    XLSX("xlsx", Kind.DOCUMENT, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FileType::zip),
    PPTX("pptx", Kind.DOCUMENT, "application/vnd.openxmlformats-officedocument.presentationml.presentation", FileType::zip),

    ZIP("zip", Kind.ARCHIVE, "application/zip", FileType::zip),
    SEVEN_Z("7z", Kind.ARCHIVE, "application/x-7z-compressed", h -> starts(h, '7', 'z', 0xBC, 0xAF, 0x27, 0x1C)),
    GZ("gz", Kind.ARCHIVE, "application/gzip", h -> starts(h, 0x1F, 0x8B)),
    TGZ("tgz", Kind.ARCHIVE, "application/gzip", h -> starts(h, 0x1F, 0x8B)),
    TAR("tar", Kind.ARCHIVE, "application/x-tar", h -> ascii(h, 257, "ustar")),
    RAR("rar", Kind.ARCHIVE, "application/vnd.rar", h -> ascii(h, 0, "Rar!\u001A\u0007"));

    enum Kind { IMAGE, DOCUMENT, ARCHIVE }

    /** 魔数检查需要读取的文件头字节数（tar 的标记位于 257 偏移）。 */
    static final int HEAD_BYTES = 512;

    final String extension;
    final Kind kind;
    final String contentType;
    private final Predicate<byte[]> signature;

    FileType(String extension, Kind kind, String contentType, Predicate<byte[]> signature) {
        this.extension = extension;
        this.kind = kind;
        this.contentType = contentType;
        this.signature = signature;
    }

    static Optional<FileType> fromFileName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.extension.equals(ext)).findFirst();
    }

    boolean matches(byte[] head) {
        return signature.test(head);
    }

    private static boolean starts(byte[] h, int... sig) {
        if (h.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if ((h[i] & 0xFF) != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] h, int offset, String text) {
        byte[] t = text.getBytes(StandardCharsets.ISO_8859_1);
        return h.length >= offset + t.length && Arrays.equals(h, offset, offset + t.length, t, 0, t.length);
    }

    private static boolean ole(byte[] h) {
        return starts(h, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
    }

    private static boolean zip(byte[] h) {
        return starts(h, 'P', 'K', 0x03, 0x04);
    }

    /** 文本文件：非空且不含 NUL 字节，排除被改名的二进制文件。 */
    private static boolean looksLikeText(byte[] h) {
        if (h.length == 0) {
            return false;
        }
        for (byte b : h) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }
}
