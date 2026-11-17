package com.netflixbar.gallery.atmosphere;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Shared lightweight photo/video style. Only background pixels are processed. */
public final class SmallBackdropProcessor {
    public static final int WIDTH = 240, HEIGHT = 135;
    public static final float BRIGHTNESS = 0.6f, SATURATION = 0.7f, COVER_ZOOM = 1.08f;
    public static final long UPDATE_MS = 80;
    public static final int VIDEO_SMOOTH_RADIUS = 4;

    public static final class Frame {
        public final Bitmap bitmap;
        public final int averageColor;
        public final float coverZoom;

        Frame(Bitmap bitmap, int averageColor, float coverZoom) {
            this.bitmap = bitmap;
            this.averageColor = averageColor;
            this.coverZoom = coverZoom;
        }
    }

    public Frame process(Bitmap source, boolean blur) {
        Frame base = processBase(source, blur, WIDTH, HEIGHT, COVER_ZOOM);
        if (base.bitmap == null) return base;
        Bitmap softened;
        try {
            // Only 32,400 pixels: soften the low-resolution texture before filtered enlargement.
            softened = AtmosphereBlur.software(base.bitmap, VIDEO_SMOOTH_RADIUS);
        } finally {
            base.bitmap.recycle();
        }
        return new Frame(softened, base.averageColor, base.coverZoom);
    }

    private static Paint stylePaint() {
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        paint.setFilterBitmap(true);
        ColorMatrix saturation = new ColorMatrix();
        saturation.setSaturation(SATURATION);
        ColorMatrix brightness = new ColorMatrix(new float[]{
                BRIGHTNESS, 0, 0, 0, 0, 0, BRIGHTNESS, 0, 0, 0,
                0, 0, BRIGHTNESS, 0, 0, 0, 0, 0, 1, 0});
        brightness.postConcat(saturation);
        paint.setColorFilter(new ColorMatrixColorFilter(brightness));
        return paint;
    }

    /** Color styling for an already-blurred/fused photo background, without changing its dimensions. */
    public Bitmap applyStyle(Bitmap source) {
        Bitmap styled = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        new Canvas(styled).drawBitmap(source, 0, 0, stylePaint());
        return styled;
    }

    private Frame processBase(Bitmap source, boolean blur, int width, int height, float zoom) {
        if (source == null || source.isRecycled()) throw new IllegalArgumentException("A live source bitmap is required");
        Paint paint = stylePaint();
        Bitmap sample = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        new Canvas(sample).drawBitmap(source, null, new Rect(0, 0, 32, 32), paint);
        int[] pixels = new int[32 * 32];
        sample.getPixels(pixels, 0, 32, 0, 0, 32, 32);
        sample.recycle();
        long red = 0, green = 0, blue = 0;
        for (int color : pixels) {
            red += (color >> 16) & 255;
            green += (color >> 8) & 255;
            blue += color & 255;
        }
        int mean = 0xff000000 | ((int) (red / pixels.length) << 16)
                | ((int) (green / pixels.length) << 8) | (int) (blue / pixels.length);
        if (!blur) return new Frame(null, mean, zoom);
        // Styling happens exactly once, exclusively on background pixels.
        Bitmap small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float scale = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        float w = source.getWidth() * scale, h = source.getHeight() * scale;
        new Canvas(small).drawBitmap(source, null,
                new RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), paint);
        return new Frame(small, mean, zoom);
    }

    /** Still photos retain their larger background budget and one-time blur strength. */
    public Frame processPhoto(Bitmap source, boolean blur) {
        return processPhoto(source, blur, source.getWidth(), source.getHeight());
    }

    public Frame processPhoto(Bitmap source, boolean blur, int targetWidth, int targetHeight) {
        int[] size = BackdropPolicy.photoSize(targetWidth, targetHeight);
        Frame base = processBase(source, blur, size[0], size[1], BackdropPolicy.PHOTO_COVER_ZOOM);
        if (base.bitmap == null) return base;
        Bitmap softened;
        try {
            softened = AtmosphereBlur.blur(base.bitmap, BackdropPolicy.photoSigma(size[0], size[1]));
        } finally {
            base.bitmap.recycle();
        }
        return new Frame(softened, base.averageColor, base.coverZoom);
    }
}
