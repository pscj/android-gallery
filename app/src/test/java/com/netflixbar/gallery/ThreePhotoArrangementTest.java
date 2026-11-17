package com.netflixbar.gallery;

import org.junit.Test;
import static org.junit.Assert.*;

public class ThreePhotoArrangementTest {
    @Test public void allOrientationCombinationsClassifyRegardlessOfOrder() {
        for (int mask = 0; mask < 8; mask++) {
            int[] widths = new int[3], heights = new int[3];
            int portraits = Integer.bitCount(mask);
            for (int i = 0; i < 3; i++) { widths[i] = (mask & (1 << i)) != 0 ? 600 : 1200; heights[i] = 900; }
            ThreePhotoArrangement.Kind expected = portraits == 3 ? ThreePhotoArrangement.Kind.PORTRAITS
                    : portraits == 2 ? ThreePhotoArrangement.Kind.REDRAW
                    : portraits == 1 ? ThreePhotoArrangement.Kind.PORTRAIT_TWO_LANDSCAPES : ThreePhotoArrangement.Kind.LANDSCAPES;
            assertEquals(expected, ThreePhotoArrangement.classify(widths, heights, new boolean[3]));
        }
    }

    @Test public void oneVideoParticipatesInOrientationClassificationButTwoAreRejected() {
        assertEquals(ThreePhotoArrangement.Kind.PORTRAIT_TWO_LANDSCAPES, ThreePhotoArrangement.classify(new int[]{600,1200,1200},
                new int[]{900,900,900}, new boolean[]{false,true,false}));
        assertEquals(ThreePhotoArrangement.Kind.UNSUPPORTED, ThreePhotoArrangement.classify(new int[]{600,1200,1200},
                new int[]{900,900,900}, new boolean[]{true,true,false}));
        assertEquals(ThreePhotoArrangement.Kind.UNSUPPORTED, ThreePhotoArrangement.classify(new int[2],new int[2],new boolean[2]));
    }

    @Test public void eachVideoPositionSupportsAllThreePlayableArrangements() {
        for (int video = 0; video < 3; video++) {
            boolean[] flags = new boolean[3]; flags[video] = true;
            assertEquals(ThreePhotoArrangement.Kind.PORTRAITS, ThreePhotoArrangement.classify(
                    new int[]{600,600,600}, new int[]{900,900,900}, flags));
            assertEquals(ThreePhotoArrangement.Kind.LANDSCAPES, ThreePhotoArrangement.classify(
                    new int[]{1200,1200,1200}, new int[]{900,900,900}, flags));
            assertEquals(ThreePhotoArrangement.Kind.PORTRAIT_TWO_LANDSCAPES, ThreePhotoArrangement.classify(
                    new int[]{600,1200,1200}, new int[]{900,900,900}, flags));
            assertEquals(ThreePhotoArrangement.Kind.REDRAW, ThreePhotoArrangement.classify(
                    new int[]{600,600,1200}, new int[]{900,900,900}, flags));
        }
    }

    @Test public void referenceLayoutsUseDifferentColumnRatiosAndLeaveSpaceForTheGap() {
        assertEquals(1147,ThreePhotoArrangement.leftWidth(1920,8,ThreePhotoArrangement.Kind.LANDSCAPES));
        assertEquals(1032,ThreePhotoArrangement.leftWidth(1920,8,ThreePhotoArrangement.Kind.PORTRAIT_TWO_LANDSCAPES));
    }
}
