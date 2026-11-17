package com.netflixbar.gallery;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ThreePortraitViewTest {
    @Test public void foregroundIsUntintedWithEqualGapsAndBackgroundAtTopAndBottom() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            java.util.List<Bitmap> photos = new java.util.ArrayList<>();
            for (int color : new int[]{Color.RED,Color.GREEN,Color.BLUE}) {
                Bitmap bitmap=Bitmap.createBitmap(90,100,Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(color); photos.add(bitmap);
            }
            Bitmap background=Bitmap.createBitmap(30,20,Bitmap.Config.ARGB_8888);
            background.eraseColor(Color.YELLOW);
            EqualHeightPhotoLayout.Slot[] slots=EqualHeightPhotoLayout.calculate(new int[]{90,90,90},new int[]{100,100,100},300,200,8);
            ThreePortraitView view=new ThreePortraitView(InstrumentationRegistry.getInstrumentation().getTargetContext());
            view.layout(0,0,300,200);
            view.show(new ThreePortraitView.Frame(photos,background,slots,300,200));
            Bitmap screen=Bitmap.createBitmap(300,200,Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(screen));
            assertEquals(Color.YELLOW,screen.getPixel(0,0));
            assertEquals(Color.YELLOW,screen.getPixel(299,199));
            assertEquals(Color.RED,screen.getPixel(0,100));
            assertEquals(Color.BLUE,screen.getPixel(299,100));
            for(int i=0;i<2;i++) for(int x=slots[i].right;x<slots[i+1].left;x++) assertEquals(Color.BLACK,screen.getPixel(x,100));
            for(int i=0;i<3;i++) assertEquals(new int[]{Color.RED,Color.GREEN,Color.BLUE}[i],screen.getPixel((slots[i].left+slots[i].right)/2,100));
        });
    }
}
