package com.netflixbar.gallery;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/** Queries current published images, keeping only one selected stream in memory. */
final class GalleryRandomImages implements WebSettingsServer.Images {
    private final ContentResolver resolver;
    private final java.io.File cacheDirectory;

    GalleryRandomImages(Context context) {
        resolver = context.getContentResolver();
        cacheDirectory = context.getCacheDir();
    }

    @Override public WebSettingsServer.Image resize(WebSettingsServer.Image source, int width, int height)
            throws IOException {
        return ImageCropper.resize(source.input, cacheDirectory, width, height);
    }

    @Override public WebSettingsServer.Image random(String orientation) throws IOException {
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String selection = Build.VERSION.SDK_INT >= 29 ? "is_pending=0" : null;
        WebSettingsServer.Image selected = null;
        Random random = new Random();
        int eligible = 0;
        try (Cursor cursor = resolver.query(collection, new String[]{MediaStore.Images.Media._ID},
                selection, null, null)) {
            if (cursor == null) throw new IOException("媒体库查询失败");
            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("服务已停止");
                Uri uri = ContentUris.withAppendedId(collection, cursor.getLong(0));
                WebSettingsServer.Image candidate;
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream input = resolver.openInputStream(uri)) {
                        if (input == null) continue;
                        BitmapFactory.decodeStream(input, null, bounds);
                    }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType == null
                            || !bounds.outMimeType.matches("image/[a-zA-Z0-9.+-]+")) continue;
                    int exif;
                    try (InputStream input = resolver.openInputStream(uri)) {
                        if (input == null) continue;
                        exif = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL);
                    }
                    if (!ImageOrientation.matches(orientation, bounds.outWidth, bounds.outHeight, exif)) continue;
                    InputStream input = resolver.openInputStream(uri);
                    if (input == null) continue;
                    candidate = new WebSettingsServer.Image(input, bounds.outMimeType);
                } catch (IOException e) {
                    // Deleted, unmounted or unreadable entries cannot participate.
                    continue;
                }
                if (random.nextInt(++eligible) == 0) {
                    WebSettingsServer.Image previous = selected;
                    selected = candidate;
                    if (previous != null) previous.close();
                } else {
                    candidate.close();
                }
            }
            WebSettingsServer.Image result = selected;
            selected = null;
            return result;
        } finally {
            if (selected != null) selected.close();
        }
    }
}
