package com.netflixbar.gallery;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

final class ImageResolution {
    final int width;
    final int height;

    private ImageResolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    static ImageResolution parse(String path) {
        String width = null;
        String height = null;
        int query = path.indexOf('?');
        if (query < 0) return null;
        for (String parameter : path.substring(query + 1).split("&", -1)) {
            String[] pair = parameter.split("=", 2);
            String name = decode(pair[0]);
            if (!"width".equals(name) && !"height".equals(name)) continue;
            if (pair.length != 2) throw new IllegalArgumentException();
            String value = decode(pair[1]);
            if ("width".equals(name)) {
                if (width != null) throw new IllegalArgumentException();
                width = value;
            } else {
                if (height != null) throw new IllegalArgumentException();
                height = value;
            }
        }
        if (width == null && height == null) return null;
        int w = dimension(width);
        int h = dimension(height);
        if ((long) w * h > 8388608L) throw new IllegalArgumentException();
        return new ImageResolution(w, h);
    }

    private static int dimension(String value) {
        if (value == null || !value.matches("[0-9]+")) throw new IllegalArgumentException();
        int result = Integer.parseInt(value);
        if (result < 1 || result > 4096) throw new IllegalArgumentException();
        return result;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
