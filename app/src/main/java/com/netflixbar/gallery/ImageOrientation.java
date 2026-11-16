package com.netflixbar.gallery;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/** Direction rules shared by the HTTP adapter and MediaStore reader. */
final class ImageOrientation {
    static String parse(String path) {
        int query = path.indexOf('?');
        String result = "landscape";
        boolean found = false;
        if (query < 0) return result;
        for (String parameter : path.substring(query + 1).split("&", -1)) {
            String[] pair = parameter.split("=", 2);
            if (!"orientation".equals(decode(pair[0]))) continue;
            if (found || pair.length != 2) throw new IllegalArgumentException();
            found = true;
            result = decode(pair[1]);
            if (!"portrait".equals(result) && !"landscape".equals(result)) {
                throw new IllegalArgumentException();
            }
        }
        return result;
    }

    static boolean matches(String orientation, int width, int height, int exif) {
        if (width <= 0 || height <= 0) return false;
        // EXIF transpose, rotate 90, transverse and rotate 270 swap the axes.
        if (exif >= 5 && exif <= 8) {
            int swap = width;
            width = height;
            height = swap;
        }
        return "portrait".equals(orientation) ? height > width : width >= height;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
