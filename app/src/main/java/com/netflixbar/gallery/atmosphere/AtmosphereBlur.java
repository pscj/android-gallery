package com.netflixbar.gallery.atmosphere;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.PixelFormat;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.util.Log;

final class AtmosphereBlur {
    private AtmosphereBlur() { }

    static Bitmap blur(Bitmap source, float sigma) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                return accelerated(source, sigma);
            } catch (RuntimeException | LinkageError e) {
                Log.w("DualAtmosphere", "GPU blur unavailable; using bounded bitmap blur", e);
            }
        }
        return software(source, Math.max(1, Math.round(sigma)));
    }

    static Bitmap software(Bitmap source, int radius) {
        int width = source.getWidth(), height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        SmallBitmapSmoother.smooth(pixels, width, height, radius);
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    @androidx.annotation.RequiresApi(31)
    private static Bitmap accelerated(Bitmap source, float sigma) {
        // Android's offscreen RenderEffect path:
        // https://developer.android.com/guide/topics/renderscript/migrate
        ImageReader reader = ImageReader.newInstance(source.getWidth(), source.getHeight(),
                PixelFormat.RGBA_8888, 1, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
                        | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
        HardwareRenderer renderer = new HardwareRenderer();
        RenderNode node = new RenderNode("DualPhotoBackgroundBlur");
        try {
            renderer.setSurface(reader.getSurface());
            renderer.setContentRoot(node);
            node.setPosition(0, 0, source.getWidth(), source.getHeight());
            node.setRenderEffect(RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.MIRROR));
            Canvas canvas = node.beginRecording();
            canvas.drawBitmap(source, 0, 0, null);
            node.endRecording();
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            try (Image image = reader.acquireNextImage()) {
                if (image == null) throw new IllegalStateException("GPU blur produced no image");
                try (HardwareBuffer buffer = image.getHardwareBuffer()) {
                    if (buffer == null) throw new IllegalStateException("Missing GPU buffer");
                    Bitmap wrapped = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                    if (wrapped == null) throw new IllegalStateException("Invalid GPU bitmap");
                    try {
                        return wrapped.copy(Bitmap.Config.ARGB_8888, false);
                    } finally {
                        wrapped.recycle();
                    }
                }
            }
        } finally {
            renderer.destroy();
            node.discardDisplayList();
            reader.close();
        }
    }

}
