package com.netflixbar.gallery;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.netflixbar.gallery.atmosphere.SmallBackdropProcessor;

/** Left full-height photo region, two filled photos stacked at the right. */
final class ThreeStackView extends View {
    static final class Frame {
        final java.util.List<Bitmap> photos;
        final SmallBackdropProcessor.Frame background;
        final int width, height, leftWidth, gap;
        final boolean cropLeft;

        Frame(java.util.List<Bitmap> photos, SmallBackdropProcessor.Frame background,
              int width, int height, int leftWidth, int gap, boolean cropLeft) {
            this.photos = new java.util.ArrayList<>(photos); this.background = background;
            this.width = width; this.height = height; this.leftWidth = leftWidth; this.gap = gap;
            this.cropLeft = cropLeft;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Frame current, previous;
    private float progress = 1;
    private ValueAnimator animator;

    ThreeStackView(Context context) { super(context); setContentDescription("左一右二三图"); }

    void show(Frame frame) {
        if (animator != null) animator.cancel();
        previous = current; current = frame; progress = previous == null ? 1 : 0;
        if (previous != null) {
            animator = ValueAnimator.ofFloat(0, 1); animator.setDuration(700);
            animator.addUpdateListener(value -> {
                progress = (float) value.getAnimatedValue();
                if (progress == 1) previous = null;
                invalidate();
            });
            animator.start();
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (current == null) return;
        if (previous != null) drawFrame(canvas, previous);
        int layer = previous == null ? -1 : canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(progress * 255));
        drawFrame(canvas, current);
        if (layer >= 0) canvas.restoreToCount(layer);
    }

    private void drawFrame(Canvas canvas, Frame frame) {
        canvas.drawColor(android.graphics.Color.BLACK);
        int saved = canvas.save();
        canvas.scale(getWidth() / (float) frame.width, getHeight() / (float) frame.height);
        RightPriorityLayout layout = RightPriorityLayout.calculate(frame.width, frame.height, frame.gap,
                frame.photos.get(1).getWidth(), frame.photos.get(1).getHeight(),
                frame.photos.get(2).getWidth(), frame.photos.get(2).getHeight());
        RectF left = new RectF(0, 0, layout.leftWidth, frame.height);
        int clip = canvas.save();
        canvas.clipRect(left);
        if (!frame.cropLeft) drawPhoto(canvas, frame.background.bitmap, left, true, frame.background.coverZoom);
        drawPhoto(canvas, frame.photos.get(0), left, frame.cropLeft, 1);
        canvas.restoreToCount(clip);
        // Integer rounding may leave less than one pixel of fitting space, never crop an edge.
        drawPhoto(canvas, frame.photos.get(1), new RectF(layout.leftWidth + frame.gap, 0,
                frame.width, layout.topHeight), false, 1);
        drawPhoto(canvas, frame.photos.get(2), new RectF(layout.leftWidth + frame.gap, layout.topHeight + frame.gap,
                frame.width, frame.height), false, 1);
        canvas.restoreToCount(saved);
    }

    private void drawPhoto(Canvas canvas, Bitmap photo, RectF target, boolean crop, float zoom) {
        float scale = (crop ? Math.max(target.width() / photo.getWidth(), target.height() / photo.getHeight())
                : Math.min(target.width() / photo.getWidth(), target.height() / photo.getHeight())) * zoom;
        float width = photo.getWidth() * scale, height = photo.getHeight() * scale;
        RectF rect = new RectF(target.centerX() - width / 2, target.centerY() - height / 2,
                target.centerX() + width / 2, target.centerY() + height / 2);
        int saved = canvas.save(); canvas.clipRect(target);
        canvas.drawBitmap(photo, null, rect, paint); canvas.restoreToCount(saved);
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        animator = null; current = previous = null;
        super.onDetachedFromWindow();
    }
}
