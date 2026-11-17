package com.netflixbar.gallery;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Lay out against actual measured bounds, never a stale DisplayMetrics height. */
final class ThreeMediaLayout extends FrameLayout {
    private final int[] widths, heights;
    private final ThreePhotoArrangement.Kind kind;
    private final int gap;

    ThreeMediaLayout(Context context, int[] widths, int[] heights, ThreePhotoArrangement.Kind kind, int gap) {
        super(context);
        this.widths = widths.clone(); this.heights = heights.clone(); this.kind = kind; this.gap = gap;
        setBackgroundColor(android.graphics.Color.BLACK);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left, height = bottom - top;
        if (width <= gap * 2 + 3 || height <= gap + 2) return;
        EqualHeightPhotoLayout.Slot[] columns = kind == ThreePhotoArrangement.Kind.PORTRAITS
                ? EqualHeightPhotoLayout.calculate(widths, heights, width, height, gap) : null;
        RightPriorityLayout rightColumn = RightPriorityLayout.calculate(width, height, gap,
                widths[1], heights[1], widths[2], heights[2]);
        int leftWidth = rightColumn.leftWidth;
        int topHeight = rightColumn.topHeight;
        for (int i = 0; i < Math.min(3, getChildCount()); i++) {
            View child = getChildAt(i);
            int x, y, w, h;
            if (columns != null) {
                x = columns[i].left; y = 0; w = columns[i].right - x; h = height;
            } else {
                x = i == 0 ? 0 : leftWidth + gap;
                y = i < 2 ? 0 : topHeight + gap;
                w = i == 0 ? leftWidth : width - x;
                h = i == 0 ? height : i == 1 ? topHeight : height - y;
            }
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            child.layout(x, y, x + w, y + h);
        }
    }
}
