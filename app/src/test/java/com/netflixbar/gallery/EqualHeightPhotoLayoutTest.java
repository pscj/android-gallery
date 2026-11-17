package com.netflixbar.gallery;

import org.junit.Test;
import static org.junit.Assert.*;

public class EqualHeightPhotoLayoutTest {
    @Test public void differentPortraitRatiosFillWidthWithEqualHeightAndGaps() {
        check(new int[]{600, 600, 700}, new int[]{1200, 900, 1000}, 3840, 2160, 8);
    }

    @Test public void tallCommonHeightIsCroppedEquallyAtTopAndBottom() {
        EqualHeightPhotoLayout.Slot[] slots = check(new int[]{400,400,400}, new int[]{1200,1200,1200}, 3840,2160,8);
        for (EqualHeightPhotoLayout.Slot slot : slots) {
            assertEquals(0, slot.top); assertEquals(2160, slot.bottom);
            assertTrue(slot.sourceTop > 0);
            assertEquals(1200 - slot.sourceBottom, slot.sourceTop, 0.001);
        }
    }

    @Test public void shorterCommonHeightLeavesEqualTopBottomBackground() {
        EqualHeightPhotoLayout.Slot[] slots = check(new int[]{900,900,900}, new int[]{1000,1000,1000}, 1920,1080,8);
        for (EqualHeightPhotoLayout.Slot slot : slots) {
            assertTrue(slot.top > 0);
            assertEquals(slot.top, 1080 - slot.bottom, 1);
        }
    }

    @Test public void lastColumnAbsorbsRoundingOnOddScreenSizes() {
        check(new int[]{601,503,777}, new int[]{1000,1100,1234},1919,1079,7);
        check(new int[]{601,503,777}, new int[]{1000,1100,1234},3838,2158,14);
    }

    @Test public void correctedExifPortraitDimensionsAreAcceptedAndLandscapeRejected() {
        assertTrue(ThreePortraitPolicy.supports(new int[]{600,700,800},new int[]{900,1000,1200},new boolean[3]));
        assertFalse(ThreePortraitPolicy.supports(new int[]{900,700,800},new int[]{600,1000,1200},new boolean[3]));
    }

    @Test public void unsupportedRoundsIncludeVideoAndInsufficientPhotos() {
        assertFalse(ThreePortraitPolicy.supports(new int[]{600,600,600},new int[]{900,900,900},new boolean[]{false,true,false}));
        assertFalse(ThreePortraitPolicy.supports(new int[]{600,600},new int[]{900,900},new boolean[2]));
        assertFalse(ThreePortraitPolicy.supports(new int[]{0,600,600},new int[]{900,900,900},new boolean[3]));
    }

    private static EqualHeightPhotoLayout.Slot[] check(int[] widths,int[] heights,int w,int h,int gap) {
        EqualHeightPhotoLayout.Slot[] slots=EqualHeightPhotoLayout.calculate(widths,heights,w,h,gap);
        assertEquals(0,slots[0].left); assertEquals(w,slots[2].right);
        for(int i=0;i<3;i++) {
            EqualHeightPhotoLayout.Slot slot=slots[i];
            assertEquals(slots[0].top,slot.top); assertEquals(slots[0].bottom,slot.bottom);
            if(i>0) assertEquals(gap,slot.left-slots[i-1].right);
            assertEquals((slot.sourceRight-slot.sourceLeft)/(slot.sourceBottom-slot.sourceTop),
                    (slot.right-slot.left)/(double)(slot.bottom-slot.top),0.000001);
            assertTrue(slot.sourceTop>=-0.001 && slot.sourceLeft>=-0.001);
            assertTrue(slot.sourceRight<=widths[i]+0.001 && slot.sourceBottom<=heights[i]+0.001);
        }
        return slots;
    }
}
