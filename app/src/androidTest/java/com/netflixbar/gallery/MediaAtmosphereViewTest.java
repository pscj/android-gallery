package com.netflixbar.gallery;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.netflixbar.gallery.atmosphere.SmallBackdropProcessor;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MediaAtmosphereViewTest {
    @Test public void enlargedVideoBackgroundKeepsTheStyledBrightnessAndSaturation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Bitmap source = Bitmap.createBitmap(240, 135, Bitmap.Config.ARGB_8888);
            source.eraseColor(Color.RED);
            SmallBackdropProcessor.Frame frame = new SmallBackdropProcessor().process(source, true);
            MediaAtmosphereView view = new MediaAtmosphereView(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            view.layout(0, 0, 1920, 1080);
            view.show(frame, true);
            Bitmap output = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(output));
            for (int[] point : new int[][]{{0, 0}, {1900, 20}, {960, 540}, {1919, 1079}}) {
                int color = output.getPixel(point[0], point[1]);
                assertEquals(117, Color.red(color), 1);
                assertEquals(10, Color.green(color), 1);
                assertEquals(10, Color.blue(color), 1);
            }
            assertEquals(Color.RED, source.getPixel(120, 67));
            view.clear();
            output.recycle();
            frame.bitmap.recycle();
            source.recycle();
        });
    }
}
