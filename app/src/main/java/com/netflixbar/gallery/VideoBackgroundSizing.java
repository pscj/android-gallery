package com.netflixbar.gallery;

/** Snapshot dimensions only; foreground video size and aspect ratio are never changed. */
final class VideoBackgroundSizing {
    private VideoBackgroundSizing() { }

    static int[] sample(int width, int height) {
        if (width <= 0 || height <= 0) return new int[]{240, 135};
        // Keep at least 240 horizontal samples, including portrait and small source surfaces.
        // Bound extreme portrait surfaces; this is a background snapshot, never foreground sizing.
        return new int[]{240, (int) Math.max(1L, Math.min(960L, Math.round(240.0 * height / width)))};
    }
}
