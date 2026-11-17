package com.netflixbar.gallery.atmosphere;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class AtmosphereMathTest {
    private static final int WIDTH = 384, HEIGHT = 216;

    @Test public void sampleRecordsMeanColorLumaAndBoundedGain() {
        AtmosphereMath.Sample sample = AtmosphereMath.sample(new int[]{0xff646464, 0xffc8c8c8});
        assertEquals(150, sample.red, 0.001);
        assertEquals(150, sample.luma, 0.001);
        assertEquals(65.0 / 150, sample.gain, 0.001);
        assertEquals(1.8, new AtmosphereMath.Sample(0, 0, 0).gain, 0.001);
        assertEquals(0.3, new AtmosphereMath.Sample(255, 255, 255).gain, 0.001);
    }

    @Test public void blendIsContinuousAcrossWholeScreenTransition() {
        assertEquals(0, AtmosphereMath.crossfadeWeight(0.38), 0.00001);
        assertEquals(0.5, AtmosphereMath.crossfadeWeight(0.5), 0.00001);
        assertEquals(1, AtmosphereMath.crossfadeWeight(0.62), 0.00001);
        double previous = 0;
        for (int x = 0; x < 1000; x++) {
            double value = AtmosphereMath.crossfadeWeight(x / 999.0);
            assertTrue(value >= previous);
            assertTrue(value - previous < 0.007);
            previous = value;
        }
    }

    @Test public void brightAndDarkPhotosMeetAcceptanceWithoutChangingInputPixels() {
        int[] left = solid(0xffaaaaaa), right = solid(0xff3d3d3d);
        int[] unchanged = left.clone();
        AtmosphereMath.Sample a = AtmosphereMath.sample(left), b = AtmosphereMath.sample(right);
        int[] normalizedLeft = transform(left, AtmosphereMath.colorMatrix(a, b));
        int[] normalizedRight = transform(right, AtmosphereMath.colorMatrix(b, a));
        AtmosphereMath.Result result = AtmosphereMath.compose(normalizedLeft, normalizedRight, WIDTH, HEIGHT, a, b);
        assertFalse(result.fallback);
        assertTrue(result.difference <= 0.15);
        assertEquals(62, AtmosphereMath.sample(result.pixels).luma, 1);
        assertArrayEquals(unchanged, left);
    }

    @Test public void matrixKeepsAlphaAndDefersSharedStyling() {
        AtmosphereMath.Sample sample = new AtmosphereMath.Sample(65, 65, 65);
        float[] matrix = AtmosphereMath.colorMatrix(sample, sample);
        assertEquals(1, matrix[18], 0);
        assertEquals(1, matrix[0] - matrix[5], 0.0001);
        assertEquals(0, matrix[4], 0.0001);
    }

    @Test public void nearBlackAndWhiteUseStableMeanColorGradient() {
        AtmosphereMath.Sample black = new AtmosphereMath.Sample(0, 0, 0);
        AtmosphereMath.Sample white = new AtmosphereMath.Sample(255, 255, 255);
        AtmosphereMath.Result result = AtmosphereMath.compose(null, null, WIDTH, HEIGHT, black, white);
        assertTrue(result.fallback);
        assertTrue(result.difference <= 0.15);
        assertEquals(62, AtmosphereMath.sample(result.pixels).luma, 1);
    }

    @Test public void clampWithResidualDifferenceTriggersFallback() {
        assertTrue(AtmosphereMath.needsFallback(new AtmosphereMath.Sample(20, 20, 20),
                new AtmosphereMath.Sample(170, 170, 170)));
    }

    @Test public void internalLeftDarkRightBrightProfileIsCorrectedOrFallsBack() {
        int[] left = new int[WIDTH * HEIGHT], right = solid(0xff777777);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int channel = 5 + x * 240 / (WIDTH - 1);
                left[y * WIDTH + x] = gray(channel);
            }
        }
        AtmosphereMath.Sample a = AtmosphereMath.sample(left), b = AtmosphereMath.sample(right);
        AtmosphereMath.Result result = AtmosphereMath.compose(transform(left, AtmosphereMath.colorMatrix(a, b)),
                transform(right, AtmosphereMath.colorMatrix(b, a)), WIDTH, HEIGHT, a, b);
        assertTrue(result.difference <= 0.15);
    }

    @Test public void saturatedAndExtremeMeanColorsAlwaysProduceBalancedFallback() {
        int[] colors = {0xff000000, 0xffffffff, 0xff0000ff, 0xffffff00, 0xffff0000, 0xff00ff00};
        for (int left : colors) {
            for (int right : colors) {
                AtmosphereMath.Sample a = AtmosphereMath.sample(new int[]{left});
                AtmosphereMath.Sample b = AtmosphereMath.sample(new int[]{right});
                AtmosphereMath.Result result = AtmosphereMath.compose(solid(left), solid(right), WIDTH, HEIGHT, a, b);
                assertTrue(Integer.toHexString(left) + "/" + Integer.toHexString(right), result.difference <= 0.15);
            }
        }
    }

    @Test public void warmCoolPhotosRemainBalancedAcrossMultipleSpatialProfiles() {
        Random random = new Random(42);
        for (int attempt = 0; attempt < 12; attempt++) {
            int[] left = new int[WIDTH * HEIGHT], right = new int[left.length];
            for (int i = 0; i < left.length; i++) {
                int x = i % WIDTH;
                int y = i / WIDTH;
                left[i] = color(50 + x * 170 / WIDTH, 30 + y * 130 / HEIGHT, random.nextInt(90));
                right[i] = color(random.nextInt(70), 30 + x * 100 / WIDTH, 80 + y * 150 / HEIGHT);
            }
            AtmosphereMath.Sample a = AtmosphereMath.sample(left), b = AtmosphereMath.sample(right);
            AtmosphereMath.Result result = AtmosphereMath.compose(transform(left, AtmosphereMath.colorMatrix(a, b)),
                    transform(right, AtmosphereMath.colorMatrix(b, a)), WIDTH, HEIGHT, a, b);
            assertTrue(result.difference <= 0.15);
        }
    }

    private static int[] solid(int color) {
        int[] pixels = new int[WIDTH * HEIGHT];
        Arrays.fill(pixels, color);
        return pixels;
    }

    private static int[] transform(int[] pixels, float[] matrix) {
        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int r = (pixels[i] >> 16) & 255, g = (pixels[i] >> 8) & 255, b = pixels[i] & 255;
            result[i] = color(Math.round(matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[4]),
                    Math.round(matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[9]),
                    Math.round(matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[14]));
        }
        return result;
    }

    private static int gray(int value) { return color(value, value, value); }

    private static int color(int r, int g, int b) {
        return 0xff000000 | (Math.max(0, Math.min(255, r)) << 16)
                | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }
}
