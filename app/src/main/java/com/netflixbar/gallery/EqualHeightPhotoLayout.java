package com.netflixbar.gallery;

/** Pure geometry over orientation-corrected photo dimensions. Coordinates never stretch a photo. */
final class EqualHeightPhotoLayout {
    static final class Slot {
        final double sourceLeft, sourceTop, sourceRight, sourceBottom;
        final int left, top, right, bottom;

        Slot(double sl, double st, double sr, double sb, int l, int t, int r, int b) {
            sourceLeft = sl; sourceTop = st; sourceRight = sr; sourceBottom = sb;
            left = l; top = t; right = r; bottom = b;
        }
    }

    static Slot[] calculate(int[] widths, int[] heights, int screenWidth, int screenHeight, int gap) {
        if (widths.length == 0 || widths.length != heights.length || screenHeight <= 0
                || gap < 0 || screenWidth - gap * (widths.length - 1) < widths.length) {
            throw new IllegalArgumentException("Invalid photo layout dimensions");
        }
        double sum = 0;
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] <= 0 || heights[i] <= 0) throw new IllegalArgumentException("Missing photo size");
            sum += widths[i] / (double) heights[i];
        }
        int available = screenWidth - gap * (widths.length - 1);
        double commonHeight = available / sum;
        int visibleHeight = Math.min(screenHeight, Math.max(1, (int) Math.round(commonHeight)));
        int top = (screenHeight - visibleHeight) / 2;
        Slot[] slots = new Slot[widths.length];
        int x = 0;
        for (int i = 0; i < widths.length; i++) {
            int remaining = widths.length - i - 1;
            int width = i == widths.length - 1 ? screenWidth - x
                    : (int) Math.round(commonHeight * widths[i] / heights[i]);
            width = Math.max(1, Math.min(width, screenWidth - x - remaining * (gap + 1)));
            // Match each rounded target exactly by subpixel source cropping, never by stretching.
            double scale = Math.max(commonHeight / heights[i], width / (double) widths[i]);
            scale = Math.max(scale, visibleHeight / (double) heights[i]);
            double sourceWidth = width / scale, sourceHeight = visibleHeight / scale;
            double sl = (widths[i] - sourceWidth) / 2, st = (heights[i] - sourceHeight) / 2;
            slots[i] = new Slot(sl, st, sl + sourceWidth, st + sourceHeight,
                    x, top, x + width, top + visibleHeight);
            x += width + gap;
        }
        return slots;
    }
}
