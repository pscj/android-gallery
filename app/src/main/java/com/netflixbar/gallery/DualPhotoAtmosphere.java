package com.netflixbar.gallery;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import com.netflixbar.gallery.atmosphere.PhotoAtmosphereProcessor;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** All decoding, sampling, normalization, blurring and compositing run on one bounded worker. */
final class DualPhotoAtmosphere implements AutoCloseable {
    static final class Source {
        final String key;
        final Callable<Bitmap> decode;

        Source(String key, Callable<Bitmap> decode) {
            this.key = key;
            this.decode = decode;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            runnable.run();
        }, "dual-photo-atmosphere");
        thread.setDaemon(true);
        return thread;
    });
    private final PhotoAtmosphereProcessor processor = new PhotoAtmosphereProcessor();

    Future<?> submit(Runnable task) { return worker.submit(task); }

    ThreeStackView.Frame prepareStack(java.util.List<Source> sources, int width, int height, int gap,
                                       ThreePhotoArrangement.Kind kind) throws Exception {
        java.util.List<Bitmap> photos = new java.util.ArrayList<>();
        for (Source source : sources) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            Bitmap photo = source.decode.call();
            if (photo == null) throw new IllegalStateException("无法解码三图");
            photos.add(photo);
        }
        RightPriorityLayout layout = RightPriorityLayout.calculate(width, height, gap,
                photos.get(1).getWidth(), photos.get(1).getHeight(), photos.get(2).getWidth(), photos.get(2).getHeight());
        com.netflixbar.gallery.atmosphere.SmallBackdropProcessor.Frame background =
                new com.netflixbar.gallery.atmosphere.SmallBackdropProcessor()
                        .processPhoto(photos.get(0), true, layout.leftWidth, height);
        return new ThreeStackView.Frame(photos, background, width, height, layout.leftWidth, gap, false);
    }

    ThreePortraitView.Frame prepareThree(java.util.List<Source> sources, int width, int height, int gap) throws Exception {
        java.util.List<Bitmap> photos = new java.util.ArrayList<>();
        java.util.List<String> ids = new java.util.ArrayList<>();
        int[] widths = new int[3], heights = new int[3];
        for (int i = 0; i < sources.size(); i++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            Bitmap photo = sources.get(i).decode.call();
            if (photo == null) throw new IllegalStateException("无法解码三竖图");
            photos.add(photo); ids.add(sources.get(i).key);
            widths[i] = photo.getWidth(); heights[i] = photo.getHeight();
        }
        EqualHeightPhotoLayout.Slot[] slots = EqualHeightPhotoLayout.calculate(widths, heights, width, height, gap);
        PhotoAtmosphereProcessor.Result background = processor.generate(ids, photos, width, height);
        return new ThreePortraitView.Frame(photos, background.bitmap, slots, width, height);
    }

    TwoLandscapeView.Frame prepare(Source leftSource, Source rightSource, int width, int height, int gap) throws Exception {
        Bitmap left = leftSource.decode.call();
        Bitmap right = rightSource.decode.call();
        if (left == null || right == null) throw new IllegalStateException("无法解码双横图");
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        PhotoAtmosphereProcessor.Result background = processor.generate(leftSource.key, left,
                rightSource.key, right, width, height);
        int bw = background.bitmap.getWidth(), bh = background.bitmap.getHeight();
        Log.i("DualAtmosphere", "background " + bw + "x" + bh + ", fallback="
                + background.fallback + ", left/right luma difference=" + background.brightnessDifference);
        int[] slotWidths = TwoImageSizing.widths(width - gap, height,
                left.getWidth(), left.getHeight(), right.getWidth(), right.getHeight());
        RectF leftRect = photoRect(0, slotWidths[0], height, left);
        RectF rightRect = photoRect(slotWidths[0] + gap, slotWidths[1], height, right);
        Bitmap shadow = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas shadowCanvas = new Canvas(shadow);
        shadowCanvas.scale(bw / (float) width, bh / (float) height);
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float scale4k = width / 3840f;
        shadowPaint.setColor(0x73000000);
        shadowPaint.setShadowLayer(48 * scale4k, 0, 16 * scale4k, 0x73000000);
        shadowCanvas.drawRect(leftRect, shadowPaint);
        shadowCanvas.drawRect(rightRect, shadowPaint);
        return new TwoLandscapeView.Frame(left, right, background.bitmap, shadow, leftRect, rightRect,
                width, height, background.fallback, background.brightnessDifference);
    }

    private static RectF photoRect(int x, int slotWidth, int height, Bitmap bitmap) {
        float scale = Math.min(slotWidth / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        float start = x + (slotWidth - w) / 2;
        float top = (height - h) / 2;
        return new RectF(start, top, start + w, top + h);
    }

    @Override public void close() {
        worker.shutdownNow();
        processor.close();
    }
}
