package com.netflixbar.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;

import androidx.exifinterface.media.ExifInterface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ImageCropperTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void correctsEveryExifOrientationWithoutExtraRotation() {
        Bitmap source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 40; x++) {
                source.setPixel(x, y, y < 10 ? (x < 20 ? Color.RED : Color.GREEN)
                        : (x < 20 ? Color.BLUE : Color.YELLOW));
            }
        }
        int[][] expected = {
                {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW},
                {Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE},
                {Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED},
                {Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN},
                {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW},
                {Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN},
                {Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED},
                {Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE}};
        try {
            for (int exif = 1; exif <= 8; exif++) {
                int w = exif >= 5 ? 20 : 40;
                int h = exif >= 5 ? 40 : 20;
                Bitmap output = ImageCropper.render(source, exif, w, h);
                try {
                    int[] corners = {output.getPixel(w / 4, h / 4), output.getPixel(3 * w / 4, h / 4),
                            output.getPixel(w / 4, 3 * h / 4), output.getPixel(3 * w / 4, 3 * h / 4)};
                    assertArrayEquals("EXIF " + exif, expected[exif - 1], corners);
                } finally { output.recycle(); }
            }
        } finally { source.recycle(); }
    }

    @Test public void cropsCentrallyAndUpscalesWithoutPadding() {
        assertEquals(new Rect(50, 0, 150, 100), ImageCropper.centerRegion(200, 100, 1, 1));
        assertEquals(new Rect(0, 50, 100, 150), ImageCropper.centerRegion(100, 200, 1, 1));
        Bitmap source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.RED);
        new android.graphics.Canvas(source).drawRect(50, 0, 150, 100, paint(Color.BLUE));
        Bitmap output = ImageCropper.render(source, 1, 300, 300);
        try {
            assertEquals(Color.BLUE, output.getPixel(10, 150));
            assertEquals(Color.BLUE, output.getPixel(289, 150));
            assertEquals(300, output.getWidth());
            assertEquals(300, output.getHeight());
        } finally { source.recycle(); output.recycle(); }
    }

    @Test public void writesExactJpegDimensionsWhiteBackgroundAndCleansFiles() throws Exception {
        File cache = testDirectory();
        Bitmap source = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888);
        try {
            source.eraseColor(Color.TRANSPARENT);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, png));
            try (WebSettingsServer.Image result = ImageCropper.resize(
                    new ByteArrayInputStream(png.toByteArray()), cache, 160, 90)) {
                assertEquals("image/jpeg", result.mime);
                Bitmap decoded = BitmapFactory.decodeStream(result.input);
                assertNotNull(decoded);
                try {
                    assertEquals(160, decoded.getWidth());
                    assertEquals(90, decoded.getHeight());
                    assertEquals(Color.WHITE, decoded.getPixel(80, 45));
                } finally { decoded.recycle(); }
            }
            assertEquals(0, cache.list().length);
        } finally { source.recycle(); cache.delete(); }
    }

    @Test public void appliesExifOnActualEncodedSource() throws Exception {
        File cache = testDirectory();
        File jpeg = new File(cache, "source.jpg");
        Bitmap source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.RED);
        new android.graphics.Canvas(source).drawRect(40, 0, 80, 40, paint(Color.BLUE));
        try {
            try (FileOutputStream file = new FileOutputStream(jpeg)) {
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 95, file));
            }
            ExifInterface exif = new ExifInterface(jpeg.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, "6");
            exif.saveAttributes();
            try (FileInputStream input = new FileInputStream(jpeg);
                 WebSettingsServer.Image result = ImageCropper.resize(input, cache, 40, 80)) {
                Bitmap decoded = BitmapFactory.decodeStream(result.input);
                assertNotNull(decoded);
                try {
                    assertTrue(Color.red(decoded.getPixel(20, 10)) > 220);
                    assertTrue(Color.blue(decoded.getPixel(20, 70)) > 220);
                    assertEquals(40, decoded.getWidth());
                    assertEquals(80, decoded.getHeight());
                } finally { decoded.recycle(); }
            }
        } finally { source.recycle(); jpeg.delete(); cache.delete(); }
    }

    @Test public void rejectsInvalidImageAndReleasesTemporaryFiles() throws Exception {
        File cache = testDirectory();
        try {
            try {
                ImageCropper.resize(new ByteArrayInputStream(new byte[]{1, 2, 3}), cache, 100, 100);
                fail("Invalid image must not produce a JPEG");
            } catch (IOException expected) {
                assertEquals(0, cache.list().length);
            }
        } finally { cache.delete(); }
    }

    @Test public void animatedGifProducesFirstFrame() throws Exception {
        // Two 1x1 frames: black followed by white.
        byte[] gif = android.util.Base64.decode(
                "R0lGODlhAQABAIAAAAAAAP///yH5BAAAAAAALAAAAAABAAEAAAICRAEAIfkEAAAAAAAsAAAAAAEAAQAAAgJMAQA7", 0);
        File cache = testDirectory();
        try (WebSettingsServer.Image result = ImageCropper.resize(new ByteArrayInputStream(gif), cache, 20, 10)) {
            Bitmap decoded = BitmapFactory.decodeStream(result.input);
            assertNotNull(decoded);
            try {
                assertEquals(20, decoded.getWidth());
                assertEquals(10, decoded.getHeight());
                assertTrue(Color.red(decoded.getPixel(10, 5)) < 10);
            } finally { decoded.recycle(); }
        } finally { cache.delete(); }
    }

    private File testDirectory() throws IOException {
        File directory = new File(context.getCacheDir(), "crop-test-" + java.util.UUID.randomUUID());
        if (!directory.mkdir()) throw new IOException("Cannot create test directory");
        return directory;
    }

    private android.graphics.Paint paint(int color) {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(color);
        return paint;
    }
}
