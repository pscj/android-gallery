package com.netflixbar.gallery;

import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.netflixbar.gallery.atmosphere.SmallBackdropProcessor;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ThreeStackViewTest {
    @Test public void bothLayoutsPrioritizeCompleteRightPhotosAndFitLeftWithBlur() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (boolean portrait : new boolean[]{false,true}) {
                java.util.List<Bitmap> photos = new java.util.ArrayList<>();
                Bitmap left = Bitmap.createBitmap(portrait ? 40 : 160,90,Bitmap.Config.ARGB_8888);
                left.eraseColor(Color.RED); photos.add(left);
                for (int color : new int[]{Color.GREEN,Color.BLUE}) {
                    Bitmap photo=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);
                    photo.eraseColor(color);photos.add(photo);
                }
                SmallBackdropProcessor.Frame background=new SmallBackdropProcessor().processPhoto(left,true,160,200);
                ThreeStackView view=new ThreeStackView(InstrumentationRegistry.getInstrumentation().getTargetContext());
                view.layout(0,0,300,200);
                view.show(new ThreeStackView.Frame(photos,background,300,200,160,8,false));
                Bitmap rendered=Bitmap.createBitmap(300,200,Bitmap.Config.ARGB_8888);
                view.draw(new Canvas(rendered));
                RightPriorityLayout layout=RightPriorityLayout.calculate(300,200,8,160,90,160,90);
                int rightX=layout.leftWidth+8;
                assertEquals(Color.GREEN,rendered.getPixel(rightX+5,5));
                assertEquals(Color.GREEN,rendered.getPixel(294,layout.topHeight-5));
                assertEquals(Color.BLUE,rendered.getPixel(rightX+5,layout.topHeight+13));
                assertEquals(Color.BLUE,rendered.getPixel(294,194));
                assertEquals(Color.BLACK,rendered.getPixel(layout.leftWidth+4,50));
                assertEquals(Color.BLACK,rendered.getPixel(200,layout.topHeight+4));
                assertEquals(Color.RED,rendered.getPixel(layout.leftWidth/2,100));
                int blurred=rendered.getPixel(portrait ? 0 : layout.leftWidth/2,portrait ? 100 : 0);
                assertTrue(Color.red(blurred)>0);
                assertNotEquals(Color.RED,blurred);
            }
        });
    }
}
