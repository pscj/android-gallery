package com.netflixbar.gallery;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Owns only files created by this upload; never replaces or deletes pre-existing media. */
final class GalleryImageUploads implements WebSettingsServer.Uploads {
    private static final String DIRECTORY = "Pictures/GalleryUploads/";
    private final Context context;
    private final Runnable onPublished;

    GalleryImageUploads(Context context, Runnable onPublished) {
        this.context = context.getApplicationContext();
        this.onPublished = onPublished;
    }

    private static final class Destination {
        final String volume;
        final File directory;
        Destination(String volume, File directory) { this.volume = volume; this.directory = directory; }
    }

    private Destination destination() throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            StorageManager manager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Set<String> names = MediaStore.getExternalVolumeNames(context);
            List<StorageVolume> volumes = new ArrayList<>(manager.getStorageVolumes());
            volumes.sort(Comparator.comparing(volume -> volume.getUuid() == null ? "" : volume.getUuid()));
            for (StorageVolume volume : volumes) {
                String uuid = volume.getUuid();
                if (volume.isRemovable() && Environment.MEDIA_MOUNTED.equals(volume.getState()) && uuid != null
                        && names.contains(uuid.toLowerCase(Locale.ROOT))) {
                    File root = Build.VERSION.SDK_INT >= 30 ? volume.getDirectory() : new File("/storage", uuid);
                    if (root != null) return new Destination(uuid.toLowerCase(Locale.ROOT), new File(root, DIRECTORY));
                }
            }
        } else {
            for (File external : context.getExternalFilesDirs(null)) {
                if (external != null && Environment.isExternalStorageRemovable(external)
                        && Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(external))) {
                    File root = external;
                    for (int i = 0; i < 4; i++) root = root.getParentFile();
                    if (root != null) return new Destination(null, new File(root, DIRECTORY));
                }
            }
        }
        throw new IOException("没有可写的存储卡，请插入存储卡并确认已挂载");
    }

    @Override public JsonObject status() {
        JsonObject result = new JsonObject();
        result.addProperty("maxBytes", WebSettingsServer.MAX_UPLOAD_BYTES);
        try {
            result.addProperty("directory", destination().directory.getAbsolutePath());
            result.addProperty("available", true);
        } catch (IOException | SecurityException e) {
            result.addProperty("available", false);
            result.addProperty("error", "没有可用的存储卡，请检查存储卡及访问权限");
        }
        return result;
    }

    @Override public JsonObject receive(String name, long length, InputStream input) throws IOException {
        String savedName = UploadPolicy.distinctName(name);
        if (length <= 0 || length > WebSettingsServer.MAX_UPLOAD_BYTES) throw new IOException("图片大小超出限制");
        Destination target = destination();
        try {
            JsonObject result = Build.VERSION.SDK_INT >= 29
                    ? receiveModern(target, savedName, length, input) : receiveLegacy(target, savedName, length, input);
            onPublished.run();
            return result;
        } catch (SecurityException e) {
            throw new IOException("没有存储卡写入权限，请在设备上授予图库存储权限", e);
        }
    }

    private JsonObject receiveModern(Destination target, String name, long length, InputStream input) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        String extension = UploadPolicy.extension(name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/" + (extension.matches("jpe?g") ? "jpeg" : extension));
        values.put(MediaStore.Images.Media.RELATIVE_PATH, DIRECTORY);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.getContentUri(target.volume), values);
        if (uri == null) throw new IOException("无法创建图片，请检查存储卡空间和权限");
        boolean published = false;
        try {
            try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("无法写入存储卡");
                copy(input, output, length);
            }
            BitmapFactory.Options image = inspect(() -> resolver.openInputStream(uri));
            if (Thread.currentThread().isInterrupted()) throw new IOException("上传已停止");
            values.clear();
            values.put(MediaStore.Images.Media.MIME_TYPE, image.outMimeType);
            values.put(MediaStore.Images.Media.WIDTH, image.outWidth);
            values.put(MediaStore.Images.Media.HEIGHT, image.outHeight);
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) throw new IOException("图片索引发布失败");
            published = true;
            String actualName = name;
            try (Cursor row = resolver.query(uri, new String[]{MediaStore.Images.Media.DISPLAY_NAME}, null, null, null)) {
                if (row != null && row.moveToFirst()) actualName = row.getString(0);
            }
            return result(target, actualName, uri);
        } finally {
            if (!published) {
                try { resolver.delete(uri, null, null); } catch (RuntimeException ignored) { }
            }
        }
    }

    private JsonObject receiveLegacy(Destination target, String name, long length, InputStream input) throws IOException {
        if (!target.directory.isDirectory() && !target.directory.mkdirs()) {
            throw new IOException("无法创建上传目录，请在设备上授予存储卡目录写入权限");
        }
        File temporary = File.createTempFile(".gallery-upload-", ".pending", target.directory);
        File completed = new File(target.directory, name);
        boolean published = false;
        boolean moved = false;
        try {
            try (OutputStream output = new FileOutputStream(temporary)) { copy(input, output, length); }
            BitmapFactory.Options image = inspect(() -> new FileInputStream(temporary));
            if (completed.exists() || !temporary.renameTo(completed)) throw new IOException("图片保存失败");
            moved = true;
            CountDownLatch indexed = new CountDownLatch(1);
            Uri[] result = {null};
            MediaScannerConnection.scanFile(context, new String[]{completed.getAbsolutePath()},
                    new String[]{image.outMimeType}, (path, uri) -> { result[0] = uri; indexed.countDown(); });
            try {
                if (!indexed.await(20, TimeUnit.SECONDS) || result[0] == null) throw new IOException("图片媒体扫描失败");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("上传已停止", e);
            }
            published = true;
            return result(target, name, result[0]);
        } finally {
            temporary.delete();
            if (!published && moved) completed.delete();
        }
    }

    private interface Source { InputStream open() throws IOException; }

    private static BitmapFactory.Options inspect(Source source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = source.open()) {
            if (input == null) throw new IOException("无法校验图片");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 100000 || bounds.outHeight > 100000
                || !UploadPolicy.supportsMime(bounds.outMimeType)) throw new IOException("文件不是可读取的受支持图片");
        BitmapFactory.Options sample = new BitmapFactory.Options();
        sample.inSampleSize = 1;
        while (bounds.outWidth / sample.inSampleSize > 1024 || bounds.outHeight / sample.inSampleSize > 1024) {
            sample.inSampleSize *= 2;
        }
        try (InputStream input = source.open()) {
            if (input == null) throw new IOException("无法校验图片");
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, sample);
            if (bitmap == null) throw new IOException("图片内容损坏，未保存");
            bitmap.recycle();
        }
        return bounds;
    }

    static void copy(InputStream input, OutputStream output, long length) throws IOException {
        byte[] buffer = new byte[32768];
        long received = 0;
        while (received < length) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("上传已停止");
            int count = input.read(buffer, 0, (int) Math.min(buffer.length, length - received));
            if (count < 0) throw new IOException("图片传输中断，未保存");
            output.write(buffer, 0, count);
            received += count;
        }
        output.flush();
    }

    private static JsonObject result(Destination target, String name, Uri uri) {
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("path", new File(target.directory, name).getAbsolutePath());
        result.addProperty("uri", uri.toString());
        result.addProperty("indexed", true);
        return result;
    }
}
