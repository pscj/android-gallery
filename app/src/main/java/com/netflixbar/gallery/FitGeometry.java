package com.netflixbar.gallery;

/** One FIT geometry for still photos and moving photos; no foreground cropping. */
final class FitGeometry {
    static final class Rect {
        final float left, top, width, height;
        final double gapFraction;

        Rect(float left, float top, float width, float height, double gapFraction) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.gapFraction = gapFraction;
        }

        boolean usesBlur() { return gapFraction >= 0.05 - 0.000001; }
    }

    static double aspect(int width, int height, double pixelAspectRatio, int rotation) {
        if (width <= 0 || height <= 0) return 16.0 / 9;
        double ratio = width * (pixelAspectRatio > 0 ? pixelAspectRatio : 1) / height;
        int normalizedRotation = (rotation % 360 + 360) % 360;
        return normalizedRotation == 90 || normalizedRotation == 270 ? 1 / ratio : ratio;
    }

    static Rect fit(int targetWidth, int targetHeight, double displayAspect) {
        double w = Math.max(1, targetWidth), h = Math.max(1, targetHeight);
        double ratio = displayAspect > 0 && !Double.isNaN(displayAspect) && !Double.isInfinite(displayAspect)
                ? displayAspect : w / h;
        double photoWidth = Math.min(w, h * ratio);
        double photoHeight = photoWidth / ratio;
        return new Rect((float) ((w - photoWidth) / 2), (float) ((h - photoHeight) / 2),
                (float) photoWidth, (float) photoHeight, Math.max(0, 1 - photoWidth * photoHeight / (w * h)));
    }
}
