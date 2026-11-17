package com.netflixbar.gallery;

/** Fit both right photos completely in one column, then give the remaining width to the left. */
final class RightPriorityLayout {
    final int leftWidth, rightWidth, topHeight, bottomHeight;

    private RightPriorityLayout(int left, int right, int top, int bottom) {
        leftWidth = left; rightWidth = right; topHeight = top; bottomHeight = bottom;
    }

    static RightPriorityLayout calculate(int width, int height, int gap,
                                         int topWidth, int topHeight, int bottomWidth, int bottomHeight) {
        if (width <= gap + 1 || height <= gap + 1 || gap < 0 || topWidth <= 0 || topHeight <= 0
                || bottomWidth <= 0 || bottomHeight <= 0) throw new IllegalArgumentException("Invalid right column");
        double topRatio = topHeight / (double) topWidth, bottomRatio = bottomHeight / (double) bottomWidth;
        int available = height - gap;
        int right = Math.max(1, Math.min(width - gap - 1,
                (int) Math.floor(available / (topRatio + bottomRatio))));
        int top = Math.max(1, Math.min(available - 1, (int) Math.round(available * topRatio / (topRatio + bottomRatio))));
        return new RightPriorityLayout(width - gap - right, right, top, available - top);
    }
}
