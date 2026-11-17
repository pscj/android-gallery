package com.netflixbar.gallery;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.List;

/** An immutable, fully prepared screen is committed at once. */
final class ThreePortraitView extends View {
    static final class Frame {
        final List<Bitmap> photos;
        final Bitmap background;
        final EqualHeightPhotoLayout.Slot[] slots;
        final int width, height;

        Frame(List<Bitmap> photos, Bitmap background, EqualHeightPhotoLayout.Slot[] slots, int width, int height) {
            this.photos = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(photos));
            this.background = background; this.slots = slots; this.width = width; this.height = height;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Frame current, previous;
    private float progress = 1;
    private ValueAnimator animator;

    ThreePortraitView(Context context) { super(context); setContentDescription("三张竖图"); }

    void show(Frame frame) {
        if (animator != null) animator.cancel();
        previous = current; current = frame; progress = previous == null ? 1 : 0;
        if (previous != null) {
            animator = ValueAnimator.ofFloat(0, 1);
            animator.setDuration(700);
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
        int layer = previous == null ? -1 : canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(255 * progress));
        drawFrame(canvas, current);
        if (layer >= 0) canvas.restoreToCount(layer);
    }

    private void drawFrame(Canvas canvas, Frame frame) {
        canvas.drawBitmap(frame.background, null, new RectF(0, 0, getWidth(), getHeight()), paint);
        int saved = canvas.save();
        canvas.scale(getWidth() / (float) frame.width, getHeight() / (float) frame.height);
        for (int i = 0; i < frame.photos.size(); i++) {
            EqualHeightPhotoLayout.Slot slot = frame.slots[i];
            RectF target = new RectF(slot.left, slot.top, slot.right, slot.bottom);
            Matrix matrix = new Matrix();
            matrix.setRectToRect(new RectF((float) slot.sourceLeft, (float) slot.sourceTop,
                    (float) slot.sourceRight, (float) slot.sourceBottom), target, Matrix.ScaleToFit.FILL);
            int clip = canvas.save();
            canvas.clipRect(target);
            canvas.drawBitmap(frame.photos.get(i), matrix, paint);
            canvas.restoreToCount(clip);
            if (i < frame.photos.size() - 1) {
                paint.setColor(android.graphics.Color.BLACK);
                canvas.drawRect(slot.right, 0, frame.slots[i + 1].left, frame.height, paint);
                paint.setColor(android.graphics.Color.WHITE);
            }
        }
        canvas.restoreToCount(saved);
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        animator = null; current = previous = null;
        super.onDetachedFromWindow();
    }
}
