package com.netflixbar.gallery.atmosphere;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.LruCache;

/**
 * Reusable background processing, independent of Fragment, layout, foregrounds and transitions.
 * Invoke generate() on a worker thread. IDs should include file modification/version information.
 * Returned backgrounds are shared cache entries: callers must not mutate or recycle them.
 */
public final class PhotoAtmosphereProcessor implements AutoCloseable {
    private volatile boolean closed;
    public static final class Result {
        public final Bitmap bitmap;
        public final boolean fallback;
        public final double brightnessDifference;

        private Result(Bitmap bitmap, AtmosphereMath.Result result) {
            this.bitmap = bitmap;
            fallback = result.fallback;
            brightnessDifference = result.difference;
        }
    }

    private final LruCache<String, Result> backgrounds = new LruCache<String, Result>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Result value) { return value.bitmap.getAllocationByteCount(); }
    };
    private final LruCache<String, AtmosphereMath.Sample> samples = new LruCache<>(64);
    private final SmallBackdropProcessor style = new SmallBackdropProcessor();

    /** Still-photo path. Video snapshots use SmallBackdropProcessor.process() directly. */
    public synchronized Result generateSingle(String id, Bitmap image, int targetWidth, int targetHeight) {
        if (closed) throw new java.util.concurrent.CancellationException();
        if (targetWidth <= 0 || targetHeight <= 0) throw new IllegalArgumentException("Positive photo dimensions required");
        String key = "photo-style|" + id + "|" + targetWidth + "x" + targetHeight;
        Result cached = backgrounds.get(key);
        if (cached != null) return cached;
        Bitmap bitmap = style.processPhoto(image, true, targetWidth, targetHeight).bitmap;
        Result result = measuredResult(bitmap, false);
        backgrounds.put(key, result);
        return result;
    }

    /** Generates a full-canvas background; no assumption is made about the foreground layout. */
    public synchronized Result generate(String leftId, Bitmap left, String rightId, Bitmap right,
                                        int targetWidth, int targetHeight) {
        return generate(java.util.Arrays.asList(leftId, rightId), java.util.Arrays.asList(left, right),
                targetWidth, targetHeight);
    }

    /** Ordered sources, independent of the number or arrangement of foreground photos. */
    public synchronized Result generate(java.util.List<String> ids, java.util.List<Bitmap> images,
                                        int targetWidth, int targetHeight) {
        if (closed) throw new java.util.concurrent.CancellationException();
        if (ids == null || images == null || ids.size() != images.size() || images.size() < 2
                || targetWidth <= 0 || targetHeight <= 0) throw new IllegalArgumentException("Invalid photo sources");
        StringBuilder cacheKey = new StringBuilder(targetWidth + "x" + targetHeight);
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == null || images.get(i) == null) throw new IllegalArgumentException("Missing photo");
            cacheKey.append('|').append(ids.get(i).length()).append(':').append(ids.get(i));
        }
        String key = cacheKey.toString();
        Result cached = backgrounds.get(key);
        if (cached != null) return cached;
        float scale = Math.min(0.25f, Math.min(960f / targetWidth, 540f / targetHeight));
        int width = Math.max(1, Math.round(targetWidth * scale));
        int height = Math.max(1, Math.round(targetHeight * scale));
        AtmosphereMath.Sample[] measurements = new AtmosphereMath.Sample[images.size()];
        for (int i = 0; i < images.size(); i++) measurements[i] = sample(ids.get(i), images.get(i));
        int[][] pixels = new int[images.size()][];
        if (!AtmosphereMath.needsFallback(measurements)) {
            for (int i = 0; i < images.size(); i++) pixels[i] = normalizedBackground(images.get(i), width,
                    height, targetWidth, measurements[i], measurements);
        }
        AtmosphereMath.Result composed = AtmosphereMath.compose(pixels, width, height, measurements);
        Bitmap normalized = Bitmap.createBitmap(composed.pixels, width, height, Bitmap.Config.ARGB_8888);
        Bitmap styled;
        try { styled = style.applyStyle(normalized); } finally { normalized.recycle(); }
        Result result = measuredResult(styled, composed.fallback);
        if (result.brightnessDifference > 0.15) {
            styled.recycle();
            AtmosphereMath.Result fallback = AtmosphereMath.gradientFallback(width, height, measurements);
            Bitmap gradient = Bitmap.createBitmap(fallback.pixels, width, height, Bitmap.Config.ARGB_8888);
            try { styled = style.applyStyle(gradient); } finally { gradient.recycle(); }
            result = measuredResult(styled, true);
        }
        if (closed || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        backgrounds.put(key, result);
        return result;
    }

    private static Result measuredResult(Bitmap bitmap, boolean fallback) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return new Result(bitmap, new AtmosphereMath.Result(pixels, fallback,
                AtmosphereMath.sideDifference(pixels, width, height)));
    }

    private AtmosphereMath.Sample sample(String key, Bitmap image) {
        AtmosphereMath.Sample cached = samples.get(key);
        if (cached != null) return cached;
        Bitmap small = Bitmap.createScaledBitmap(image, 32, 32, true);
        int[] pixels = new int[32 * 32];
        small.getPixels(pixels, 0, 32, 0, 0, 32, 32);
        if (small != image) small.recycle();
        AtmosphereMath.Sample result = AtmosphereMath.sample(pixels);
        samples.put(key, result);
        return result;
    }

    private static int[] normalizedBackground(Bitmap image, int width, int height, int screenWidth,
                                               AtmosphereMath.Sample self, AtmosphereMath.Sample[] all) {
        int sampleWidth = Math.min(image.getWidth(), Math.max(1, Math.round(480 * screenWidth / 3840f)));
        int sampleHeight = Math.max(1, Math.round(image.getHeight() * sampleWidth / (float) image.getWidth()));
        Bitmap small = Bitmap.createScaledBitmap(image, sampleWidth, sampleHeight, true);
        Bitmap cover = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float scale = Math.max(width / (float) small.getWidth(), height / (float) small.getHeight()) * 1.15f;
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((width - small.getWidth() * scale) / 2, (height - small.getHeight() * scale) / 2);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        new Canvas(cover).drawBitmap(small, matrix, paint);
        if (small != image) small.recycle();
        // sigma=48px at 4K, or 12px in the final 960px background.
        Bitmap blurred;
        try {
            blurred = AtmosphereBlur.blur(cover, Math.max(1, width * 48f / 3840));
        } finally {
            cover.recycle();
        }
        Bitmap normalized = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(AtmosphereMath.colorMatrix(self, all))));
        new Canvas(normalized).drawBitmap(blurred, 0, 0, paint);
        blurred.recycle();
        int[] pixels = new int[width * height];
        normalized.getPixels(pixels, 0, width, 0, 0, width, height);
        normalized.recycle();
        return pixels;
    }

    @Override public void close() {
        closed = true;
        backgrounds.evictAll();
        samples.evictAll();
    }
}
