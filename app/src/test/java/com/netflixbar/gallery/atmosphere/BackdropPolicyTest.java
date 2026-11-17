package com.netflixbar.gallery.atmosphere;

import org.junit.Test;
import static org.junit.Assert.*;

public class BackdropPolicyTest {
    @Test public void evenSmallPhotoGapsKeepTheirBlur() {
        assertTrue(BackdropPolicy.useBlur(false, 0.01));
        assertTrue(BackdropPolicy.useBlur(false, 0.049));
        assertFalse(BackdropPolicy.useBlur(false, 0));
    }

    @Test public void fivePercentThresholdOnlyAppliesToVideos() {
        assertFalse(BackdropPolicy.useBlur(true, 0.049));
        assertTrue(BackdropPolicy.useBlur(true, 0.05));
        assertTrue(BackdropPolicy.useBlur(true, 0.051));
    }

    @Test public void landscapePhotoKeepsItsOriginalBackgroundBudget() {
        assertArrayEquals(new int[]{960, 540}, BackdropPolicy.photoSize(3840, 2160));
        assertArrayEquals(new int[]{480, 270}, BackdropPolicy.photoSize(1920, 1080));
    }

    @Test public void portraitAndMixedSlotsDoNotGetForcedToSixteenByNine() {
        assertArrayEquals(new int[]{270, 480}, BackdropPolicy.photoSize(1080, 1920));
        assertArrayEquals(new int[]{320, 270}, BackdropPolicy.photoSize(1280, 1080));
    }

    @Test public void veryLargePhotoTargetsStayWithinMemoryBudget() {
        int[] size = BackdropPolicy.photoSize(7680, 4320);
        assertArrayEquals(new int[]{960, 540}, size);
    }

    @Test public void photoSigmaAndOverscanAreNotTheVideoSettings() {
        assertEquals(12, BackdropPolicy.photoSigma(960, 540), 0.001);
        assertEquals(6, BackdropPolicy.photoSigma(480, 270), 0.001);
        assertEquals(1.15, BackdropPolicy.PHOTO_COVER_ZOOM, 0.0001);
        assertEquals(1.08, SmallBackdropProcessor.COVER_ZOOM, 0.0001);
    }
}
