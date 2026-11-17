package com.netflixbar.gallery;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.netflixbar.gallery.atmosphere.SmallBackdropProcessor;

/** A ready-made tiny background texture; drawing never regenerates or filters a video frame. */
final class MediaAtmosphereView extends View {
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF cover = new RectF();
    private SmallBackdropProcessor.Frame frame;
    private boolean blur = true;

    MediaAtmosphereView(Context context) {
        super(context);
        paint.setFilterBitmap(true);
        setId(R.id.media_atmosphere);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void show(SmallBackdropProcessor.Frame frame, boolean blur) {
        this.frame = frame;
        this.blur = blur;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (frame == null) return;
        canvas.drawColor(frame.averageColor);
        if (!blur || frame.bitmap == null) return;
        float scale = Math.max(getWidth() / (float) frame.bitmap.getWidth(),
                getHeight() / (float) frame.bitmap.getHeight()) * frame.coverZoom;
        float width = frame.bitmap.getWidth() * scale, height = frame.bitmap.getHeight() * scale;
        cover.set((getWidth() - width) / 2, (getHeight() - height) / 2,
                (getWidth() + width) / 2, (getHeight() + height) / 2);
        canvas.drawBitmap(frame.bitmap, null, cover, paint);
    }

    void clear() { frame = null; invalidate(); }

    @Override protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }
}
