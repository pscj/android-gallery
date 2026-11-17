package com.netflixbar.gallery.atmosphere;

import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SmallBackdropProcessorTest {
    @Test public void outputIs240By135AndBrightnessAppliedExactlyOnce() {
        Bitmap source = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.rgb(200, 200, 200));
        SmallBackdropProcessor.Frame result = new SmallBackdropProcessor().process(source, true);
        assertEquals(240, result.bitmap.getWidth());
        assertEquals(135, result.bitmap.getHeight());
        assertEquals(120, Color.red(result.averageColor), 1);
        assertEquals(120, Color.red(result.bitmap.getPixel(120, 67)), 1);
        assertEquals(200, Color.red(source.getPixel(0, 0)));
        source.recycle();
    }

    @Test public void smallGapOnlyNeedsAnAverageColor() {
        Bitmap source = Bitmap.createBitmap(240, 135, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.rgb(200, 200, 200));
        SmallBackdropProcessor.Frame result = new SmallBackdropProcessor().process(source, false);
        assertNull(result.bitmap);
        assertEquals(120, Color.red(result.averageColor), 1);
        source.recycle();
    }

    @Test public void videoAndPhotoBothSoftenEdgesAndKeepTheirSeparateBudgets() {
        Bitmap source = Bitmap.createBitmap(240, 135, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(source);
        canvas.drawColor(Color.BLACK);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(Color.WHITE);
        canvas.drawRect(120, 0, 240, 135, paint);
        SmallBackdropProcessor processor = new SmallBackdropProcessor();
        SmallBackdropProcessor.Frame video = processor.process(source, true);
        SmallBackdropProcessor.Frame photo = processor.processPhoto(source, true, 3840, 2160);
        int videoLeft = Color.red(video.bitmap.getPixel(118, 67));
        int videoRight = Color.red(video.bitmap.getPixel(122, 67));
        int photoLeft = Color.red(photo.bitmap.getPixel(472, 268));
        int photoRight = Color.red(photo.bitmap.getPixel(488, 268));
        assertTrue(videoLeft > 0);
        assertTrue(videoRight < 153);
        assertTrue(videoLeft < videoRight);
        assertTrue(photoLeft > 0);
        assertTrue(photoRight < 153);
        assertEquals(240, video.bitmap.getWidth());
        assertEquals(960, photo.bitmap.getWidth());
        assertEquals(video.averageColor, photo.averageColor);
        assertEquals(Color.WHITE, source.getPixel(122, 67));
        source.recycle();
        video.bitmap.recycle();
        photo.bitmap.recycle();
    }

    @Test public void saturationAndBrightnessReachTheFinalVideoBitmapExactlyOnce() {
        Bitmap source = Bitmap.createBitmap(240, 135, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.RED);
        SmallBackdropProcessor.Frame result = new SmallBackdropProcessor().process(source, true);
        int styled = result.bitmap.getPixel(120, 67);
        assertEquals(117, Color.red(styled), 1);
        assertEquals(10, Color.green(styled), 1);
        assertEquals(10, Color.blue(styled), 1);
        assertEquals(255 * 0.6 * 0.7, Color.red(styled) - Color.green(styled), 2);
        assertEquals(Color.RED, source.getPixel(120, 67));
        result.bitmap.recycle();
        source.recycle();
    }

    @Test public void photoBackdropKeepsTheActualPortraitSlotAspect() {
        Bitmap source = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.rgb(200, 200, 200));
        SmallBackdropProcessor.Frame photo = new SmallBackdropProcessor().processPhoto(source, true, 1080, 1920);
        assertEquals(270, photo.bitmap.getWidth());
        assertEquals(480, photo.bitmap.getHeight());
        assertEquals(1.15f, photo.coverZoom, 0.0001);
        assertEquals(120, Color.red(photo.bitmap.getPixel(100, 100)), 1);
        assertEquals(200, Color.red(source.getPixel(0, 0)));
        source.recycle();
        photo.bitmap.recycle();
    }
}
