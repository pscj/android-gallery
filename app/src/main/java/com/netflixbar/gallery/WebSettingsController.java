package com.netflixbar.gallery;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

/** Activity-owned service. Enabling is intentionally limited to this Activity's lifetime. */
public final class WebSettingsController {
    public static final int PORT = 8000;
    private final Context context;
    private final GallerySettings settings;
    private WebSettingsServer server;
    private String error;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable mediaListener;

    public void setMediaListener(Runnable listener) { mediaListener = listener; }

    private void mediaPublished() {
        mainHandler.post(() -> { if (mediaListener != null) mediaListener.run(); });
    }

    public WebSettingsController(Context context) {
        this.context = context.getApplicationContext();
        settings = new GallerySettings(context);
    }

    public boolean isEnabled() { return server != null && server.getPort() > 0; }

    public void enable() {
        if (isEnabled()) return;
        error = null;
        try (InputStream input = context.getAssets().open("settings.html")) {
            ByteArrayOutputStream page = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) page.write(buffer, 0, count);
            WebSettingsServer candidate = new WebSettingsServer(new WebSettingsServer.Store() {
                @Override public JsonObject read() {
                    JsonObject result = new JsonObject();
                    result.addProperty("interval", settings.getInterval());
                    result.addProperty("displayMode", settings.getMode());
                    JsonArray options = new JsonArray();
                    long[] intervals = GallerySettings.intervals();
                    String[] labels = GallerySettings.labels();
                    for (int i = 0; i < intervals.length; i++) {
                        JsonObject option = new JsonObject();
                        option.addProperty("value", intervals[i]);
                        option.addProperty("label", labels[i]);
                        options.add(option);
                    }
                    result.add("intervals", options);
                    return result;
                }

                @Override public void save(long interval, String mode) {
                    settings.save(interval, mode);
                }
            }, page.toByteArray(), new GalleryImageUploads(context, this::mediaPublished),
                    new GalleryRandomImages(context));
            candidate.start(PORT);
            server = candidate;
        } catch (IOException e) {
            error = "网页服务启动失败，请检查 8000 端口是否被占用后重试";
        }
    }

    public void disable() {
        if (server != null) server.stop();
        server = null;
        error = null;
    }

    public String getStatus() {
        if (error != null) return error;
        if (!isEnabled()) return "网页设置已关闭";
        String addresses = getAddresses();
        if (addresses.isEmpty()) return "网络已断开，请重新连接后查看网址";
        return "同一 Wi-Fi／局域网的浏览器访问：\n" + addresses
                + "\n仅在可信局域网使用。退出应用后不可访问。";
    }

    private String getAddresses() {
        StringBuilder addresses = new StringBuilder();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        if (addresses.length() > 0) addresses.append('\n');
                        addresses.append("http://").append(address.getHostAddress()).append(":").append(PORT).append("/");
                    }
                }
            }
        } catch (SocketException ignored) { }
        return addresses.toString();
    }
}
