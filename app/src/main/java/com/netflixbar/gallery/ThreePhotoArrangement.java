package com.netflixbar.gallery;

final class ThreePhotoArrangement {
    enum Kind { PORTRAITS, LANDSCAPES, PORTRAIT_TWO_LANDSCAPES, REDRAW, UNSUPPORTED }

    static Kind classify(int[] widths, int[] heights, boolean[] videos) {
        if (widths.length != 3 || heights.length != 3 || videos.length != 3) return Kind.UNSUPPORTED;
        int portraits = 0;
        int videoCount = 0;
        for (int i = 0; i < 3; i++) {
            if (videos[i]) videoCount++;
            if (videoCount > 1 || widths[i] <= 0 || heights[i] <= 0) return Kind.UNSUPPORTED;
            if (heights[i] >= widths[i]) portraits++;
        }
        if (portraits == 3) return Kind.PORTRAITS;
        if (portraits == 2) return Kind.REDRAW;
        return portraits == 1 ? Kind.PORTRAIT_TWO_LANDSCAPES : Kind.LANDSCAPES;
    }

    static int leftWidth(int width, int gap, Kind kind) {
        return Math.max(1, Math.min(width - gap - 1,
                Math.round((width - gap) * (kind == Kind.LANDSCAPES ? 0.60f : 0.54f))));
    }
}
