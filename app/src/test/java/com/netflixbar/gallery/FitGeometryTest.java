package com.netflixbar.gallery;

import org.junit.Test;
import static org.junit.Assert.*;

public class FitGeometryTest {
    @Test public void sameAspectFillsScreenWithoutBlur() {
        FitGeometry.Rect rect = FitGeometry.fit(3840, 2160, 16.0 / 9);
        assertEquals(3840, rect.width, 0.01);
        assertEquals(2160, rect.height, 0.01);
        assertFalse(rect.usesBlur());
    }

    @Test public void widerVideoHasEqualTopBottomGaps() {
        FitGeometry.Rect rect = FitGeometry.fit(3840, 2160, 2.4);
        assertEquals(3840, rect.width, 0.01);
        assertEquals(1600, rect.height, 0.01);
        assertEquals(280, rect.top, 0.01);
        assertEquals(0, rect.left, 0.01);
        assertTrue(rect.usesBlur());
    }

    @Test public void portraitVideoHasEqualLeftRightGapsWithoutCropping() {
        FitGeometry.Rect rect = FitGeometry.fit(3840, 2160, 9.0 / 16);
        assertEquals(2160, rect.height, 0.01);
        assertEquals(1215, rect.width, 0.01);
        assertEquals((3840 - 1215) / 2.0, rect.left, 0.01);
        assertTrue(rect.usesBlur());
    }

    @Test public void pixelAspectRatioAndRotationAreIncluded() {
        assertEquals(4.0 / 3, FitGeometry.aspect(720, 576, 16.0 / 15, 0), 0.0001);
        assertEquals(3.0 / 4, FitGeometry.aspect(720, 576, 16.0 / 15, 90), 0.0001);
        assertEquals(3.0 / 4, FitGeometry.aspect(720, 576, 16.0 / 15, -90), 0.0001);
    }

    @Test public void underFivePercentUsesMeanColorAtFivePercentUsesBlur() {
        assertFalse(FitGeometry.fit(1000, 1000, 1 / 0.951).usesBlur());
        assertTrue(FitGeometry.fit(1000, 1000, 1 / 0.95).usesBlur());
        assertTrue(FitGeometry.fit(1000, 1000, 1 / 0.949).usesBlur());
    }

    @Test public void fitScalesWithOutputResolution() {
        FitGeometry.Rect large = FitGeometry.fit(3840, 2160, 2.4);
        FitGeometry.Rect small = FitGeometry.fit(1920, 1080, 2.4);
        assertEquals(large.width / 2, small.width, 0.001);
        assertEquals(large.top / 2, small.top, 0.001);
        assertEquals(large.gapFraction, small.gapFraction, 0.001);
    }
}
