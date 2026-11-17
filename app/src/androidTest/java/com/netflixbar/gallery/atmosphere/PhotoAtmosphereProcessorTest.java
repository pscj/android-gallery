package com.netflixbar.gallery.atmosphere;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PhotoAtmosphereProcessorTest {
    @Test public void orderedThreeSourceInterfaceKeepsForegroundsAndCachesByOrderAndSize() {
        Bitmap first=solid(Color.rgb(170,100,60)), second=solid(Color.rgb(60,100,170)), third=solid(Color.rgb(90,130,90));
        try(PhotoAtmosphereProcessor processor=new PhotoAtmosphereProcessor()) {
            java.util.List<String> ids=java.util.Arrays.asList("a-v1","b-v1","c-v1");
            java.util.List<Bitmap> images=java.util.Arrays.asList(first,second,third);
            PhotoAtmosphereProcessor.Result result=processor.generate(ids,images,1920,1080);
            assertEquals(480,result.bitmap.getWidth()); assertEquals(270,result.bitmap.getHeight());
            assertTrue(result.brightnessDifference<=0.15);
            assertSame(result.bitmap,processor.generate(ids,images,1920,1080).bitmap);
            assertNotSame(result.bitmap,processor.generate(java.util.Arrays.asList("c-v1","b-v1","a-v1"),java.util.Arrays.asList(third,second,first),1920,1080).bitmap);
            assertNotSame(result.bitmap,processor.generate(ids,images,3840,2160).bitmap);
            assertEquals(Color.rgb(170,100,60),first.getPixel(0,0));
            assertEquals(Color.rgb(60,100,170),second.getPixel(0,0));
            assertEquals(Color.rgb(90,130,90),third.getPixel(0,0));
        }
    }
    @Test public void generatesBoundedBackgroundAndReusesPairCacheWithoutChangingPhotos() {
        Bitmap left = solid(Color.rgb(170, 170, 170));
        Bitmap right = solid(Color.rgb(61, 61, 61));
        try (PhotoAtmosphereProcessor processor = new PhotoAtmosphereProcessor()) {
            PhotoAtmosphereProcessor.Result result = processor.generate("left-v1", left, "right-v1", right, 3840, 2160);
            assertEquals(960, result.bitmap.getWidth());
            assertEquals(540, result.bitmap.getHeight());
            assertFalse(result.fallback);
            assertTrue(result.brightnessDifference <= 0.15);
            int[] rendered = new int[result.bitmap.getWidth() * result.bitmap.getHeight()];
            result.bitmap.getPixels(rendered, 0, result.bitmap.getWidth(), 0, 0,
                    result.bitmap.getWidth(), result.bitmap.getHeight());
            assertEquals(AtmosphereMath.sideDifference(rendered, result.bitmap.getWidth(), result.bitmap.getHeight()),
                    result.brightnessDifference, 0.000001);
            assertSame(result.bitmap, processor.generate("left-v1", left, "right-v1", right, 3840, 2160).bitmap);
            assertEquals(Color.rgb(170, 170, 170), left.getPixel(0, 0));
            assertEquals(Color.rgb(61, 61, 61), right.getPixel(0, 0));
            PhotoAtmosphereProcessor.Result changed = processor.generate("left-v2", left, "right-v1", right, 1920, 1080);
            assertNotSame(result.bitmap, changed.bitmap);
            assertEquals(480, changed.bitmap.getWidth());
            assertEquals(270, changed.bitmap.getHeight());
        }
        left.recycle();
        right.recycle();
    }

    @Test public void extremePhotosProduceBalancedGradientFallback() {
        Bitmap left = solid(Color.WHITE), right = solid(Color.BLACK);
        try (PhotoAtmosphereProcessor processor = new PhotoAtmosphereProcessor()) {
            PhotoAtmosphereProcessor.Result result = processor.generate("white", left, "black", right, 1920, 1080);
            assertTrue(result.fallback);
            assertTrue(result.brightnessDifference <= 0.15);
        }
        left.recycle();
        right.recycle();
    }

    @Test public void singleSourceCanBeAFrameWithoutModifyingItsColorsOrDimensions() {
        Bitmap frame = solid(Color.rgb(100, 80, 60));
        try (PhotoAtmosphereProcessor processor = new PhotoAtmosphereProcessor()) {
            PhotoAtmosphereProcessor.Result result = processor.generateSingle("video-frame-1", frame, 1080, 1920);
            assertTrue(result.brightnessDifference <= 0.15);
            assertEquals(384, frame.getWidth());
            assertEquals(216, frame.getHeight());
            assertEquals(Color.rgb(100, 80, 60), frame.getPixel(0, 0));
        }
        frame.recycle();
    }

    @Test public void samePhotoAtDifferentSlotSizesHasDistinctCachedBackgrounds() {
        Bitmap photo = solid(Color.rgb(100, 80, 60));
        try (PhotoAtmosphereProcessor processor = new PhotoAtmosphereProcessor()) {
            PhotoAtmosphereProcessor.Result portrait = processor.generateSingle("same-photo", photo, 1080, 1920);
            PhotoAtmosphereProcessor.Result landscape = processor.generateSingle("same-photo", photo, 1920, 1080);
            assertNotSame(portrait.bitmap, landscape.bitmap);
            assertEquals(270, portrait.bitmap.getWidth());
            assertEquals(480, portrait.bitmap.getHeight());
            assertEquals(480, landscape.bitmap.getWidth());
            assertEquals(270, landscape.bitmap.getHeight());
            assertSame(portrait.bitmap, processor.generateSingle("same-photo", photo, 1080, 1920).bitmap);
        }
        photo.recycle();
    }

    private static Bitmap solid(int color) {
        Bitmap bitmap = Bitmap.createBitmap(384, 216, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }
}
