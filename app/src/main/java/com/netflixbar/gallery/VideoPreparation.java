package com.netflixbar.gallery;

import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.LruCache;

import com.netflixbar.gallery.atmosphere.SmallBackdropProcessor;

/** Run when media enters the queue, before creating or starting its player. */
final class VideoPreparation {
    static final class Prepared {
        final int width, height, rotation;
        final double pixelAspectRatio, displayAspect;
        final long durationMs;
        final String path, mime;
        final SmallBackdropProcessor.Frame preview;

        Prepared(String path, String mime, int width, int height, int rotation, double pixelAspectRatio,
                 long durationMs, SmallBackdropProcessor.Frame preview) {
            this.path = path;
            this.mime = mime;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.pixelAspectRatio = pixelAspectRatio;
            this.durationMs = durationMs;
            displayAspect = FitGeometry.aspect(width, height, pixelAspectRatio, rotation);
            this.preview = preview;
        }
    }

    private final LruCache<String, Prepared> cache = new LruCache<>(8);
    private final SmallBackdropProcessor backgrounds = new SmallBackdropProcessor();

    synchronized Prepared prepare(String path) throws Exception {
        java.io.File file = new java.io.File(path);
        String key = path + "|" + file.length() + "|" + file.lastModified();
        Prepared cached = cache.get(key);
        if (cached != null) return cached;
        int width = 0, height = 0, rotation = 0;
        double pixelAspectRatio = 1;
        long duration = 0;
        String mime = "video/unknown";
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(path);
            for (int track = 0; track < extractor.getTrackCount(); track++) {
                MediaFormat format = extractor.getTrackFormat(track);
                String type = format.getString(MediaFormat.KEY_MIME);
                if (type == null || !type.startsWith("video/")) continue;
                mime = type;
                width = integer(format, "width", 0);
                height = integer(format, "height", 0);
                rotation = integer(format, "rotation-degrees", 0);
                int sarWidth = integer(format, "sar-width", 1);
                int sarHeight = integer(format, "sar-height", 1);
                if (sarWidth > 0 && sarHeight > 0) pixelAspectRatio = sarWidth / (double) sarHeight;
                if (format.containsKey(MediaFormat.KEY_DURATION)) duration = format.getLong(MediaFormat.KEY_DURATION) / 1000;
                break;
            }
        } finally {
            extractor.release();
        }
        // Do not open a second hardware decoder for a poster. Rockchip's retriever can
        // exhaust system/CMA memory even when the requested output is only 240x135.
        Bitmap neutral = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        SmallBackdropProcessor.Frame preview;
        try {
            neutral.eraseColor(0xff404040);
            preview = backgrounds.process(neutral, false);
        } finally {
            neutral.recycle();
        }
        Prepared result = new Prepared(path, mime, width, height, rotation, pixelAspectRatio, duration, preview);
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        cache.put(key, result);
        return result;
    }

    void clear() { cache.evictAll(); }

    private static int integer(MediaFormat format, String key, int fallback) {
        return format.containsKey(key) ? format.getInteger(key) : fallback;
    }

}
