package com.netflixbar.gallery.atmosphere;

/** Linear-time, clamped-edge smoothing shared by the tiny-video and software photo blur paths. */
final class SmallBitmapSmoother {
    private SmallBitmapSmoother() { }

    static void smooth(int[] pixels, int width, int height, int radius) {
        if (width <= 0 || height <= 0 || pixels.length != width * height || radius < 1) {
            throw new IllegalArgumentException("Invalid blur dimensions or radius");
        }
        int[] temporary = new int[pixels.length];
        for (int pass = 0; pass < 3; pass++) {
            box(pixels, temporary, width, height, radius, true);
            box(temporary, pixels, width, height, radius, false);
        }
    }

    private static void box(int[] input, int[] output, int width, int height, int radius, boolean horizontal) {
        int length = horizontal ? width : height;
        int lines = horizontal ? height : width;
        int stride = horizontal ? 1 : width;
        int count = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int start = horizontal ? line * width : line;
            int red = 0, green = 0, blue = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int pixel = input[start + Math.max(0, Math.min(length - 1, offset)) * stride];
                red += (pixel >> 16) & 255;
                green += (pixel >> 8) & 255;
                blue += pixel & 255;
            }
            for (int p = 0; p < length; p++) {
                output[start + p * stride] = 0xff000000 | ((red + count / 2) / count) << 16
                        | ((green + count / 2) / count) << 8 | (blue + count / 2) / count;
                int removed = input[start + Math.max(0, p - radius) * stride];
                int added = input[start + Math.min(length - 1, p + radius + 1) * stride];
                red += ((added >> 16) & 255) - ((removed >> 16) & 255);
                green += ((added >> 8) & 255) - ((removed >> 8) & 255);
                blue += (added & 255) - (removed & 255);
            }
        }
    }
}
