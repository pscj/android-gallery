package com.netflixbar.gallery;

import org.junit.Test;
import static org.junit.Assert.*;

public class RightPriorityLayoutTest {
    @Test public void rightColumnIsDerivedFromBothPhotosAndRemainingWidthGoesLeft() {
        RightPriorityLayout layout=RightPriorityLayout.calculate(1920,1080,8,1600,900,1600,900);
        assertEquals(952,layout.rightWidth);
        assertEquals(960,layout.leftWidth);
        assertEquals(536,layout.topHeight);
        assertEquals(536,layout.bottomHeight);
    }

    @Test public void differentRatiosGetDifferentHeightsAndBothFitCompletely() {
        RightPriorityLayout layout=RightPriorityLayout.calculate(3840,2160,8,1600,900,1200,900);
        assertTrue(layout.topHeight<layout.bottomHeight);
        assertEquals(3840,layout.leftWidth+8+layout.rightWidth);
        assertEquals(2160,layout.topHeight+8+layout.bottomHeight);
        assertTrue(layout.topHeight>=layout.rightWidth*900.0/1600-1);
        assertTrue(layout.bottomHeight>=layout.rightWidth*900.0/1200-1);
    }

    @Test public void oddDimensionsHaveNoUnallocatedBottomRow() {
        RightPriorityLayout layout=RightPriorityLayout.calculate(1919,1079,7,1920,1080,4032,3024);
        assertEquals(1079,layout.topHeight+7+layout.bottomHeight);
        assertEquals(1919,layout.leftWidth+7+layout.rightWidth);
    }
}
