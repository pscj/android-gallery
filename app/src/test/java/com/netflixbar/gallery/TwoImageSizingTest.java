package com.netflixbar.gallery;

import org.junit.Test;

import static org.junit.Assert.*;

public class TwoImageSizingTest {
    @Test public void differentLandscapeRatiosHaveEqualDisplayedHeight() {
        int[] widths = TwoImageSizing.widths(1912, 1080, 1920, 1080, 4000, 3000);
        assertEquals(1912, widths[0] + widths[1]);
        double firstHeight = widths[0] / (1920.0 / 1080);
        double secondHeight = widths[1] / (4000.0 / 3000);
        assertEquals(firstHeight, secondHeight, 1);
        assertTrue(firstHeight < 1080);
    }

    @Test public void portraitImagesCanFillHeightWithSideBackgrounds() {
        int[] widths = TwoImageSizing.widths(1912, 1080, 600, 900, 600, 1200);
        assertEquals(1912, widths[0] + widths[1]);
        assertTrue(widths[0] >= 1080 * 600 / 900);
        assertTrue(widths[1] >= 1080 * 600 / 1200);
    }

    @Test public void mixedPairReservesPortraitHeightAndGivesLandscapeTheRest() {
        int[] widths = TwoImageSizing.widths(1912, 1080, 600, 900, 1920, 1080);
        assertArrayEquals(new int[]{720, 1192}, widths);
        assertEquals(1080, widths[0] / (600.0 / 900), 0.01);
        assertTrue(widths[1] / (1920.0 / 1080) < 1080);
    }

    @Test public void narrowScreenStillLeavesBothSlotsVisible() {
        int[] widths = TwoImageSizing.widths(500, 1080, 900, 1000, 1920, 1080);
        assertArrayEquals(new int[]{499, 1}, widths);
    }

    @Test public void missingDimensionsFallBackToEqualWidths() {
        assertArrayEquals(new int[]{956, 956}, TwoImageSizing.widths(1912, 1080, 0, 0, 0, 0));
    }
}
