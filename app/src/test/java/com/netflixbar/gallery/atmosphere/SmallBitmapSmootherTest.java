package com.netflixbar.gallery.atmosphere;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class SmallBitmapSmootherTest {
    @Test public void solidColorsStayExactIncludingCorners() {
        int[] pixels = new int[240 * 135];
        Arrays.fill(pixels, 0xff786654);
        SmallBitmapSmoother.smooth(pixels, 240, 135, 4);
        for (int pixel : pixels) assertEquals(0xff786654, pixel);
    }

    @Test public void pixelBlocksBecomeSmoothGradientsRatherThanMosaic() {
        int width = 240, height = 135;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = ((x / 4 + y / 4) & 1) == 0 ? 20 : 220;
                pixels[y * width + x] = gray(value);
            }
        }
        SmallBitmapSmoother.smooth(pixels, width, height, 4);
        int largestStep = 0;
        for (int y = 0; y < height - 1; y++) {
            for (int x = 0; x < width - 1; x++) {
                int i = y * width + x;
                largestStep = Math.max(largestStep, Math.abs(channel(pixels[i]) - channel(pixels[i + 1])));
                largestStep = Math.max(largestStep, Math.abs(channel(pixels[i]) - channel(pixels[i + width])));
            }
        }
        assertTrue("Adjacent step was " + largestStep, largestStep < 25);
    }

    @Test public void sharpEdgesSoftenWithoutRingingOrBrightBorders() {
        int[] pixels = new int[240 * 135];
        for (int y = 0; y < 135; y++) {
            for (int x = 0; x < 240; x++) pixels[y * 240 + x] = gray(x < 120 ? 0 : 153);
        }
        SmallBitmapSmoother.smooth(pixels, 240, 135, 4);
        int previous = 0;
        for (int x = 0; x < 240; x++) {
            int value = channel(pixels[67 * 240 + x]);
            assertTrue(value >= previous);
            assertTrue(value <= 153);
            previous = value;
        }
        assertTrue(channel(pixels[67 * 240 + 118]) > 0);
        assertTrue(channel(pixels[67 * 240 + 122]) < 153);
        assertEquals(0, channel(pixels[0]));
        assertEquals(153, channel(pixels[pixels.length - 1]));
    }

    private static int channel(int color) { return color & 255; }
    private static int gray(int value) { return 0xff000000 | value << 16 | value << 8 | value; }
}
