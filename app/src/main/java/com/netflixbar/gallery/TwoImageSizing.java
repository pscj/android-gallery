package com.netflixbar.gallery;

/** Allocates side-by-side image widths without stretching either image. */
final class TwoImageSizing {
    private TwoImageSizing() { }

    static int[] widths(int availableWidth, int height, int firstWidth, int firstHeight,
                        int secondWidth, int secondHeight) {
        int total = Math.max(2, availableWidth);
        double firstRatio = ratio(firstWidth, firstHeight);
        double secondRatio = ratio(secondWidth, secondHeight);
        boolean mixed = firstHeight >= firstWidth && firstWidth > 0 && firstHeight > 0
                && secondWidth > secondHeight && secondHeight > 0;
        long desiredLeft = mixed ? Math.round(Math.max(1, height) * firstRatio)
                : Math.round(total * firstRatio / (firstRatio + secondRatio));
        int left = (int) Math.max(1, Math.min(total - 1L, desiredLeft));
        return new int[]{left, total - left};
    }

    private static double ratio(int width, int height) {
        return width > 0 && height > 0 ? width / (double) height : 1;
    }
}
