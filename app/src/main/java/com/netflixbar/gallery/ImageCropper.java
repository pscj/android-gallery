package com.netflixbar.gallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bounded source decoding, EXIF correction and JPEG rendering on a worker thread. */
final class ImageCropper {
    private static final long MAX_DECODE_PIXELS = 4194304L;
    private static final long MAX_SOURCE_BYTES = 256L * 1024 * 1024;

    static WebSettingsServer.Image resize(InputStream input, File cache, int width, int height)
            throws IOException {
        File source = File.createTempFile("gallery-crop-source-", ".tmp", cache);
        File result = null;
        Bitmap decoded = null;
        Bitmap output = null;
        try {
            try (FileOutputStream file = new FileOutputStream(source)) {
                byte[] buffer = new byte[32768];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkInterrupted();
                    total += count;
                    if (total > MAX_SOURCE_BYTES) throw new IOException("源图片超过 256 MiB 处理限制");
                    file.write(buffer, 0, count);
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("图片尺寸无效");
            int exif = new ExifInterface(source.getAbsolutePath()).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            boolean swap = exif >= 5 && exif <= 8;
            Rect region = centerRegion(bounds.outWidth, bounds.outHeight,
                    swap ? height : width, swap ? width : height);
            BitmapRegionDecoder decoder = null;
            try {
                try {
                    decoder = BitmapRegionDecoder.newInstance(source.getAbsolutePath(), false);
                } catch (IOException ignored) {
                    // Some formats (notably GIF) require whole-frame sampled decoding.
                }
                if (decoder != null) {
                    decoded = decoder.decodeRegion(region, options(region.width(), region.height(),
                            swap ? height : width, swap ? width : height));
                }
            } finally {
                if (decoder != null) decoder.recycle();
            }
            if (decoded == null) {
                decoded = BitmapFactory.decodeFile(source.getAbsolutePath(), options(
                        bounds.outWidth, bounds.outHeight, swap ? height : width, swap ? width : height));
            }
            if (decoded == null) throw new IOException("无法解码图片");
            checkInterrupted();
            output = render(decoded, exif, width, height);
            decoded.recycle();
            decoded = null;
            result = File.createTempFile("gallery-crop-result-", ".jpg", cache);
            try (FileOutputStream file = new FileOutputStream(result)) {
                if (!output.compress(Bitmap.CompressFormat.JPEG, 85, file)) throw new IOException("图片编码失败");
            }
            checkInterrupted();
            File completed = result;
            InputStream stream = new FilterInputStream(new FileInputStream(completed)) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { completed.delete(); }
                }
            };
            result = null;
            return new WebSettingsServer.Image(stream, "image/jpeg");
        } catch (OutOfMemoryError e) {
            throw new IOException("设备可用内存不足，无法处理图片", e);
        } finally {
            if (decoded != null) decoded.recycle();
            if (output != null) output.recycle();
            source.delete();
            if (result != null) result.delete();
        }
    }

    static Rect centerRegion(int width, int height, int targetWidth, int targetHeight) {
        int cropWidth = width;
        int cropHeight = height;
        if ((long) width * targetHeight > (long) height * targetWidth) {
            cropWidth = Math.max(1, (int) ((long) height * targetWidth / targetHeight));
        } else {
            cropHeight = Math.max(1, (int) ((long) width * targetHeight / targetWidth));
        }
        int left = (width - cropWidth) / 2;
        int top = (height - cropHeight) / 2;
        return new Rect(left, top, left + cropWidth, top + cropHeight);
    }

    private static BitmapFactory.Options options(int width, int height, int targetWidth, int targetHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (width / (options.inSampleSize * 2) >= targetWidth
                && height / (options.inSampleSize * 2) >= targetHeight) {
            options.inSampleSize *= 2;
        }
        while (((long) width + options.inSampleSize - 1) / options.inSampleSize
                * (((long) height + options.inSampleSize - 1) / options.inSampleSize) > MAX_DECODE_PIXELS) {
            options.inSampleSize *= 2;
        }
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return options;
    }

    static Bitmap render(Bitmap source, int exif, int width, int height) {
        float w = source.getWidth();
        float h = source.getHeight();
        boolean swap = exif >= 5 && exif <= 8;
        float orientedWidth = swap ? h : w;
        float orientedHeight = swap ? w : h;
        float x = orientedWidth;
        float y = orientedHeight;
        float[] destination;
        switch (exif) {
            case 2: destination = new float[]{x, 0, 0, 0, x, y}; break;
            case 3: destination = new float[]{x, y, 0, y, x, 0}; break;
            case 4: destination = new float[]{0, y, x, y, 0, 0}; break;
            case 5: destination = new float[]{0, 0, 0, y, x, 0}; break;
            case 6: destination = new float[]{x, 0, x, y, 0, 0}; break;
            case 7: destination = new float[]{x, y, x, 0, 0, y}; break;
            case 8: destination = new float[]{0, y, 0, 0, x, y}; break;
            default: destination = new float[]{0, 0, x, 0, 0, y}; break;
        }
        Matrix matrix = new Matrix();
        matrix.setPolyToPoly(new float[]{0, 0, w, 0, 0, h}, 0, destination, 0, 3);
        float scale = Math.max(width / orientedWidth, height / orientedHeight);
        matrix.postScale(scale, scale);
        matrix.postTranslate((width - orientedWidth * scale) / 2, (height - orientedHeight * scale) / 2);
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(output);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
            return output;
        } catch (RuntimeException | OutOfMemoryError e) {
            output.recycle();
            throw e;
        }
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("图片处理已停止");
    }
}
