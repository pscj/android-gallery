package com.netflixbar.gallery;

import android.view.View;
import android.widget.FrameLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ThreeMediaLayoutTest {
    @Test public void mixedAndLandscapeLayoutsReachActualBottomDespiteStaleChildSizes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (ThreePhotoArrangement.Kind kind : new ThreePhotoArrangement.Kind[]{
                    ThreePhotoArrangement.Kind.LANDSCAPES, ThreePhotoArrangement.Kind.PORTRAIT_TWO_LANDSCAPES}) {
                ThreeMediaLayout root = new ThreeMediaLayout(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        new int[]{600,1200,1200},new int[]{900,900,900},kind,8);
                for (int i=0;i<3;i++) root.addView(new FrameLayout(root.getContext()),new FrameLayout.LayoutParams(800,1024));
                root.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));
                root.layout(0,0,1920,1080);
                assertEquals(1080,root.getChildAt(0).getBottom());
                assertEquals(1080,root.getChildAt(2).getBottom());
                assertEquals(1920,root.getChildAt(2).getRight());
                assertEquals(8,root.getChildAt(2).getTop()-root.getChildAt(1).getBottom());
                root.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY));
                root.layout(0,0,1920,1200);
                assertEquals(1200,root.getChildAt(0).getBottom());
                assertEquals(1200,root.getChildAt(2).getBottom());
            }
        });
    }
}
