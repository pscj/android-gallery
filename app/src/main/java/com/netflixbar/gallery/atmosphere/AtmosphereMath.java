package com.netflixbar.gallery.atmosphere;

/** Background-only color and luminance processing. No foreground pixels enter this pipeline. */
final class AtmosphereMath {
    static final int PROFILE_COLUMNS = 96;

    static final class Sample {
        final double red, green, blue, luma, gain;

        Sample(double red, double green, double blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            luma = luma(red, green, blue);
            gain = clamp(65 / Math.max(1, luma), 0.3, 1.8);
        }
    }

    static final class Result {
        final int[] pixels;
        final boolean fallback;
        final double difference;

        Result(int[] pixels, boolean fallback, double difference) {
            this.pixels = pixels;
            this.fallback = fallback;
            this.difference = difference;
        }
    }

    static Sample sample(int[] pixels) {
        double r = 0, g = 0, b = 0;
        for (int pixel : pixels) {
            r += (pixel >> 16) & 255;
            g += (pixel >> 8) & 255;
            b += pixel & 255;
        }
        int count = Math.max(1, pixels.length);
        return new Sample(r / count, g / count, b / count);
    }

    static boolean needsFallback(Sample left, Sample right) {
        if (left.luma < 18 || right.luma < 18 || left.luma > 225 || right.luma > 225) return true;
        boolean clamped = left.gain == 0.3 || left.gain == 1.8
                || right.gain == 0.3 || right.gain == 1.8;
        return clamped && relativeDifference(left.luma * left.gain, right.luma * right.gain) > 0.30;
    }

    /** Normalize a pair before the shared brightness/saturation style is applied exactly once. */
    static float[] colorMatrix(Sample self, Sample other) {
        double[] a = normalizedColor(self);
        double[] b = normalizedColor(other);
        double gain = self.gain;
        float[] matrix = new float[20];
        for (int channel = 0; channel < 3; channel++) {
            for (int input = 0; input < 3; input++) {
                matrix[channel * 5 + input] = input == channel ? (float) gain : 0;
            }
            matrix[channel * 5 + 4] = (float) ((b[channel] - a[channel]) * 0.125);
        }
        matrix[18] = 1;
        return matrix;
    }

    static Result compose(int[] left, int[] right, int width, int height, Sample a, Sample b) {
        boolean fallback = needsFallback(a, b);
        int[] result = new int[width * height];
        if (fallback) {
            gradient(result, width, height, a, b);
        } else {
            for (int y = 0; y < height; y++) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                for (int x = 0; x < width; x++) {
                    double weight = crossfadeWeight((x + 0.5) / width);
                    int i = y * width + x;
                    result[i] = blend(left[i], right[i], weight);
                }
            }
            // Smooth, bounded feedback; never expose a background that misses the acceptance limit.
            flattenProfile(result, width, height);
            if (displayedSideDifference(result, width, height) > 0.15) {
                fallback = true;
                gradient(result, width, height, a, b);
            }
        }
        return new Result(result, fallback, displayedSideDifference(result, width, height));
    }

    static float[] colorMatrix(Sample self, Sample[] all) {
        double[] own = normalizedColor(self);
        double[] mean = new double[3];
        for (Sample sample : all) {
            double[] color = normalizedColor(sample);
            for (int c = 0; c < 3; c++) mean[c] += color[c] / all.length;
        }
        float[] matrix = new float[20];
        for (int c = 0; c < 3; c++) {
            matrix[c * 5 + c] = (float) self.gain;
            matrix[c * 5 + 4] = (float) ((mean[c] - own[c]) * 0.25);
        }
        matrix[18] = 1;
        return matrix;
    }

    static boolean needsFallback(Sample[] samples) {
        for (Sample a : samples) for (Sample b : samples) if (needsFallback(a, b)) return true;
        return false;
    }

    static double[] weights(double x, int count) {
        double[] result = new double[count];
        result[0] = 1;
        for (int i = 1; i < count; i++) {
            double center = i / (double) count;
            double t = clamp((x - center + 0.12) / 0.24, 0, 1);
            t = t * t * (3 - 2 * t);
            for (int j = 0; j < i; j++) result[j] *= 1 - t;
            result[i] = t;
        }
        return result;
    }

    static Result compose(int[][] sources, int width, int height, Sample[] samples) {
        boolean fallback = needsFallback(samples);
        int[] pixels = new int[width * height];
        if (fallback) return gradientFallback(width, height, samples);
        for (int x = 0; x < width; x++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            double[] weights = weights((x + 0.5) / width, samples.length);
            for (int y = 0; y < height; y++) {
                int index = y * width + x;
                double r = 0, g = 0, b = 0;
                for (int i = 0; i < sources.length; i++) {
                    int color = sources[i][index];
                    r += ((color >> 16) & 255) * weights[i];
                    g += ((color >> 8) & 255) * weights[i];
                    b += (color & 255) * weights[i];
                }
                pixels[index] = rgb(r, g, b);
            }
        }
        flattenProfile(pixels, width, height);
        if (sideDifference(pixels, width, height) > 0.15) return gradientFallback(width, height, samples);
        return new Result(pixels, false, sideDifference(pixels, width, height));
    }

    static Result gradientFallback(int width, int height, Sample[] samples) {
        int[] pixels = new int[width * height];
        for (int x = 0; x < width; x++) {
            double position = x / (double) Math.max(1, width - 1) * (samples.length - 1);
            int first = Math.min(samples.length - 1, (int) position);
            int second = Math.min(samples.length - 1, first + 1);
            int color = blend(fallbackColor(samples[first]), fallbackColor(samples[second]), position - first);
            for (int y = 0; y < height; y++) pixels[y * width + x] = color;
        }
        flattenProfile(pixels, width, height);
        return new Result(pixels, true, sideDifference(pixels, width, height));
    }

    static double crossfadeWeight(double x) {
        double t = clamp((x - 0.38) / 0.24, 0, 1);
        return t * t * (3 - 2 * t);
    }

    static Result gradientFallback(int width, int height, Sample a, Sample b) {
        int[] pixels = new int[width * height];
        gradient(pixels, width, height, a, b);
        return new Result(pixels, true, sideDifference(pixels, width, height));
    }

    private static void gradient(int[] pixels, int width, int height, Sample a, Sample b) {
        int left = fallbackColor(a);
        int right = fallbackColor(b);
        for (int x = 0; x < width; x++) {
            int color = blend(left, right, x / (double) Math.max(1, width - 1));
            for (int y = 0; y < height; y++) pixels[y * width + x] = color;
        }
        flattenProfile(pixels, width, height);
    }

    private static int fallbackColor(Sample sample) {
        if (sample.luma < 1) return rgb(62, 62, 62);
        double gain = 62 / sample.luma;
        double[] deviations = {sample.red * gain - 62, sample.green * gain - 62,
                sample.blue * gain - 62};
        double saturation = 0.7;
        for (double deviation : deviations) {
            if (deviation > 0) saturation = Math.min(saturation, 193 / deviation);
            if (deviation < 0) saturation = Math.min(saturation, -62 / deviation);
        }
        return rgb(62 + deviations[0] * saturation, 62 + deviations[1] * saturation,
                62 + deviations[2] * saturation);
    }

    private static double[] normalizedColor(Sample sample) {
        return new double[]{sample.red * sample.gain, sample.green * sample.gain, sample.blue * sample.gain};
    }

    private static void flattenProfile(int[] pixels, int width, int height) {
        int columns = Math.min(PROFILE_COLUMNS, width);
        double[] sums = new double[columns];
        int[] counts = new int[columns];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int column = x * columns / width;
                sums[column] += luma(pixels[y * width + x]);
                counts[column]++;
            }
        }
        double[] gains = new double[columns];
        for (int c = 0; c < columns; c++) {
            gains[c] = clamp(62 / Math.max(1, sums[c] / counts[c]), 0.7, 1.45);
        }
        // Three low-pass passes prevent stripes at the 96-bin boundaries.
        for (int pass = 0; pass < 3; pass++) {
            double[] smoothed = new double[columns];
            for (int c = 0; c < columns; c++) {
                for (int offset = -3; offset <= 3; offset++) {
                    smoothed[c] += gains[Math.max(0, Math.min(columns - 1, c + offset))] / 7;
                }
            }
            gains = smoothed;
        }
        for (int x = 0; x < width; x++) {
            double position = (x + 0.5) * columns / width - 0.5;
            int first = Math.max(0, Math.min(columns - 1, (int) Math.floor(position)));
            int second = Math.max(0, Math.min(columns - 1, (int) Math.floor(position) + 1));
            double t = position - Math.floor(position);
            double gain = gains[first] * (1 - t) + gains[second] * t;
            for (int y = 0; y < height; y++) {
                int index = y * width + x;
                int pixel = pixels[index];
                pixels[index] = rgb(((pixel >> 16) & 255) * gain,
                        ((pixel >> 8) & 255) * gain, (pixel & 255) * gain);
            }
        }
    }

    static double sideDifference(int[] pixels, int width, int height) {
        return sideDifference(pixels, width, height, false);
    }

    static double displayedSideDifference(int[] pixels, int width, int height) {
        return sideDifference(pixels, width, height, true);
    }

    private static double sideDifference(int[] pixels, int width, int height, boolean dimmed) {
        double left = 0, right = 0;
        int columns = Math.max(1, width / 3);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < columns; x++) {
                double shade = 1;
                if (dimmed) {
                    double distance = Math.hypot(x + 0.5 - width / 2.0, y + 0.5 - height / 2.0)
                            / Math.hypot(width / 2.0, height / 2.0);
                    shade = (1 - 31 / 255.0) * (1 - 31 / 255.0 * clamp((distance - 0.35) / 0.65, 0, 1));
                }
                left += luma(pixels[y * width + x]) * shade;
                right += luma(pixels[y * width + width - 1 - x]) * shade;
            }
        }
        return relativeDifference(left / (columns * height), right / (columns * height));
    }

    static double luma(int color) {
        return luma((color >> 16) & 255, (color >> 8) & 255, color & 255);
    }

    private static double luma(double r, double g, double b) {
        return r * 0.2126 + g * 0.7152 + b * 0.0722;
    }

    private static double relativeDifference(double a, double b) {
        return Math.abs(a - b) / Math.max(1, (a + b) / 2);
    }

    private static int blend(int a, int b, double t) {
        return rgb(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t,
                ((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t,
                (a & 255) * (1 - t) + (b & 255) * t);
    }

    private static int rgb(double r, double g, double b) {
        return 0xff000000 | ((int) Math.round(clamp(r, 0, 255)) << 16)
                | ((int) Math.round(clamp(g, 0, 255)) << 8) | (int) Math.round(clamp(b, 0, 255));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
