package com.netflixbar.gallery;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TwoLandscapeViewTest {
    @Test public void foregroundPhotosAreNeitherDimmedNorTintedByBackgroundLayers() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Bitmap left = solid(Color.RED), right = solid(Color.BLUE), background = solid(Color.YELLOW);
            Bitmap shadow = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888);
            TwoLandscapeView.Frame frame = new TwoLandscapeView.Frame(left, right, background, shadow,
                    new RectF(0, 27, 47, 53), new RectF(49, 27, 96, 53), 96, 80, false, 0);
            TwoLandscapeView view = new TwoLandscapeView(InstrumentationRegistry.getInstrumentation().getTargetContext());
            view.layout(0, 0, 96, 80);
            view.show(frame);
            Bitmap rendered = Bitmap.createBitmap(96, 80, Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(rendered));
            assertEquals(Color.RED, rendered.getPixel(24, 40));
            assertEquals(Color.BLUE, rendered.getPixel(72, 40));
            assertEquals(Color.YELLOW, rendered.getPixel(48, 0));
        });
    }

    private static Bitmap solid(int color) {
        Bitmap bitmap = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }
}
