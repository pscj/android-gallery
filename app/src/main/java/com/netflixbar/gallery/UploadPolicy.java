package com.netflixbar.gallery;

import java.util.Locale;
import java.util.UUID;

final class UploadPolicy {
    private UploadPolicy() { }

    static String extension(String name) {
        if (name == null || name.isEmpty() || name.length() > 180 || name.startsWith(".")
                || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("图片文件名无效");
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) throw new IllegalArgumentException("图片文件名无效");
        }
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extension.matches("jpe?g|png|webp|gif|bmp")) {
            throw new IllegalArgumentException("仅支持 JPEG、PNG、WebP、GIF 和 BMP 图片");
        }
        return extension;
    }

    static String distinctName(String name) {
        String extension = extension(name);
        return name.substring(0, name.lastIndexOf('.')) + "_" + UUID.randomUUID() + "." + extension;
    }

    static boolean supportsMime(String mime) {
        return "image/jpeg".equals(mime) || "image/png".equals(mime) || "image/webp".equals(mime)
                || "image/gif".equals(mime) || "image/bmp".equals(mime) || "image/x-ms-bmp".equals(mime);
    }
}
