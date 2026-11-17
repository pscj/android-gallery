package com.netflixbar.gallery;

import org.junit.Test;

import static org.junit.Assert.*;

public class VideoBackgroundSizingTest {
    @Test public void landscapeSnapshotPreservesSixteenByNine() {
        assertArrayEquals(new int[]{240, 135}, VideoBackgroundSizing.sample(3840, 2160));
    }

    @Test public void portraitSnapshotPreservesNineBySixteen() {
        assertArrayEquals(new int[]{240, 427}, VideoBackgroundSizing.sample(1080, 1920));
    }

    @Test public void cinemaAndSquareRatiosAreNotForcedToSixteenByNine() {
        assertArrayEquals(new int[]{240, 100}, VideoBackgroundSizing.sample(3840, 1600));
        assertArrayEquals(new int[]{240, 240}, VideoBackgroundSizing.sample(1000, 1000));
    }

    @Test public void smallFramesKeepAtLeast240HorizontalSamples() {
        assertArrayEquals(new int[]{240, 135}, VideoBackgroundSizing.sample(160, 90));
    }

    @Test public void unknownSizeUsesSafePreviewDimensionsOnly() {
        assertArrayEquals(new int[]{240, 135}, VideoBackgroundSizing.sample(0, 0));
    }

    @Test public void extremePortraitFramesCannotAllocateUnboundedSnapshots() {
        assertArrayEquals(new int[]{240, 960}, VideoBackgroundSizing.sample(1, Integer.MAX_VALUE));
    }
}
