package com.netflixbar.gallery.atmosphere;

/** Photo quality rules are independent of the dynamic video's 240x135/5% performance rules. */
public final class BackdropPolicy {
    public static final float PHOTO_COVER_ZOOM = 1.15f;

    private BackdropPolicy() { }

    public static boolean useBlur(boolean video, double gapFraction) {
        return video ? gapFraction >= 0.05 - 0.000001 : gapFraction > 0.000001;
    }

    public static int[] photoSize(int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) throw new IllegalArgumentException("Positive photo target dimensions required");
        double scale = Math.min(0.25, Math.min(960.0 / targetWidth, 540.0 / targetHeight));
        return new int[]{Math.max(1, (int) Math.round(targetWidth * scale)),
                Math.max(1, (int) Math.round(targetHeight * scale))};
    }

    public static float photoSigma(int width, int height) {
        return Math.max(1, 48 * Math.max(width / 3840f, height / 2160f));
    }
}
