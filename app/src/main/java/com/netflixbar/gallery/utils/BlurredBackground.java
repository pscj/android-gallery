package com.netflixbar.gallery.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

public final class BlurredBackground {
    private static final int MAX_SIZE = 96;
    private static final int RADIUS = 4;

    private BlurredBackground() {
    }

    public static Bitmap create(Bitmap source, int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Background dimensions must be positive");
        }
        // Blur a small, screen-shaped crop to bound CPU work and bitmap memory on older devices.
        float sample = MAX_SIZE / (float) Math.max(targetWidth, targetHeight);
        int width = Math.max(1, Math.round(targetWidth * sample));
        int height = Math.max(1, Math.round(targetHeight * sample));
        Bitmap background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float scale = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((width - source.getWidth() * scale) / 2f,
                (height - source.getHeight() * scale) / 2f);
        new Canvas(background).drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));

        int[] pixels = new int[width * height];
        int[] temporary = new int[pixels.length];
        background.getPixels(pixels, 0, width, 0, 0, width, height);
        // Three box-blur passes approximate a Gaussian blur. Clamp edges to avoid dark borders.
        for (int pass = 0; pass < 3; pass++) {
            blur(pixels, temporary, width, height, true);
            blur(temporary, pixels, width, height, false);
        }
        background.setPixels(pixels, 0, width, 0, 0, width, height);
        return background;
    }

    private static void blur(int[] input, int[] output, int width, int height, boolean horizontal) {
        int length = horizontal ? width : height;
        int lines = horizontal ? height : width;
        int stride = horizontal ? 1 : width;
        int count = RADIUS * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int start = horizontal ? line * width : line;
            int alpha = 0, red = 0, green = 0, blue = 0;
            for (int offset = -RADIUS; offset <= RADIUS; offset++) {
                int color = input[start + Math.max(0, Math.min(length - 1, offset)) * stride];
                alpha += color >>> 24;
                red += (color >> 16) & 255;
                green += (color >> 8) & 255;
                blue += color & 255;
            }
            for (int position = 0; position < length; position++) {
                output[start + position * stride] = (alpha / count) << 24 | (red / count) << 16
                        | (green / count) << 8 | blue / count;
                int removed = input[start + Math.max(0, position - RADIUS) * stride];
                int added = input[start + Math.min(length - 1, position + RADIUS + 1) * stride];
                alpha += (added >>> 24) - (removed >>> 24);
                red += ((added >> 16) & 255) - ((removed >> 16) & 255);
                green += ((added >> 8) & 255) - ((removed >> 8) & 255);
                blue += (added & 255) - (removed & 255);
            }
        }
    }
}
