package com.netflixbar.gallery;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;


/** Four independent layers: atmosphere, dimming, unchanged photographs, optional information. */
final class TwoLandscapeView extends View {
    static final class Frame {
        final Bitmap left, right, background, shadow;
        final RectF leftRect, rightRect;
        final int width, height;
        final boolean fallback;
        final double brightnessDifference;

        Frame(Bitmap left, Bitmap right, Bitmap background, Bitmap shadow, RectF leftRect,
              RectF rightRect, int width, int height, boolean fallback, double difference) {
            this.left = left;
            this.right = right;
            this.background = background;
            this.shadow = shadow;
            this.leftRect = leftRect;
            this.rightRect = rightRect;
            this.width = width;
            this.height = height;
            this.fallback = fallback;
            brightnessDifference = difference;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Frame current, previous;
    private float progress = 1;
    private ValueAnimator transition;

    TwoLandscapeView(Context context) {
        super(context);
        setContentDescription("两张横图");
    }

    void show(Frame frame) {
        if (transition != null) transition.cancel();
        previous = current;
        current = frame;
        progress = previous == null ? 1 : 0;
        if (previous != null) {
            transition = ValueAnimator.ofFloat(0, 1);
            transition.setDuration(700);
            transition.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                if (progress == 1) previous = null;
                invalidate();
            });
            transition.start();
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (current == null) return;
        if (previous != null && progress < 1) drawFrame(canvas, previous);
        int layer = -1;
        if (previous != null && progress < 1) {
            layer = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(progress * 255));
        }
        drawFrame(canvas, current);
        if (layer >= 0) canvas.restoreToCount(layer);
    }

    private void drawFrame(Canvas canvas, Frame frame) {
        RectF screen = new RectF(0, 0, getWidth(), getHeight());
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        // This frame is already generated for this canvas and overscanned by 1.15 before blurring.
        canvas.drawBitmap(frame.background, null, screen, paint);
        // Shadow is pre-rendered at background resolution; foreground photos never get filters.
        canvas.drawBitmap(frame.shadow, null, screen, paint);
        int state = canvas.save();
        canvas.scale(getWidth() / (float) frame.width, getHeight() / (float) frame.height);
        canvas.drawBitmap(frame.left, null, frame.leftRect, paint);
        canvas.drawBitmap(frame.right, null, frame.rightRect, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x66ffffff);
        paint.setStrokeWidth(frame.width / 3840f);
        canvas.drawRect(frame.leftRect, paint);
        canvas.drawRect(frame.rightRect, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.restoreToCount(state);
    }

    @Override protected void onDetachedFromWindow() {
        if (transition != null) transition.cancel();
        transition = null;
        previous = null;
        current = null;
        super.onDetachedFromWindow();
    }
}
