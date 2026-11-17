package com.netflixbar.gallery;

final class ThreePortraitPolicy {
    static boolean supports(int[] widths, int[] heights, boolean[] videos) {
        if (widths.length != 3 || heights.length != 3 || videos.length != 3) return false;
        for (int i = 0; i < 3; i++) {
            if (videos[i] || widths[i] <= 0 || heights[i] < widths[i]) return false;
        }
        return true;
    }
}
