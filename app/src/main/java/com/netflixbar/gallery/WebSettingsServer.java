package com.netflixbar.gallery;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/** Small, bounded HTTP server; deliberately independent of Android for socket tests. */
public final class WebSettingsServer {
    public interface Store {
        JsonObject read();
        void save(long interval, String mode);
    }

    public interface Uploads {
        JsonObject status();
        JsonObject receive(String name, long length, InputStream input) throws IOException;
    }

    public interface Images {
        Image random(String orientation) throws IOException;
        default Image resize(Image source, int width, int height) throws IOException {
            throw new IOException("不支持图片裁剪");
        }
    }

    public static final class Image implements java.io.Closeable {
        public final InputStream input;
        public final String mime;

        public Image(InputStream input, String mime) {
            if (input == null || mime == null || !mime.matches("image/[a-zA-Z0-9.+-]+")) {
                throw new IllegalArgumentException("Invalid image response");
            }
            this.input = input;
            this.mime = mime;
        }

        @Override public void close() throws IOException { input.close(); }
    }

    public static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024;
    private final Uploads uploads;
    private final Images images;
    private final Semaphore uploadSlot = new Semaphore(1);
    private final Semaphore cropSlot = new Semaphore(1);
    private final Set<Socket> clients = Collections.newSetFromMap(new ConcurrentHashMap<Socket, Boolean>());
    private ThreadPoolExecutor workers;
    private final Store store;
    private final byte[] page;
    private final Gson gson = new Gson();
    private volatile ServerSocket server;

    public WebSettingsServer(Store store, byte[] page) {
        this(store, page, null);
    }

    public WebSettingsServer(Store store, byte[] page, Uploads uploads) {
        this(store, page, uploads, null);
    }

    public WebSettingsServer(Store store, byte[] page, Uploads uploads, Images images) {
        this.images = images;
        this.uploads = uploads;
        this.store = store;
        this.page = page.clone();
    }

    public synchronized void start(int port) throws IOException {
        if (server != null) return;
        ServerSocket listening = new ServerSocket(port, 4);
        server = listening;
        workers = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(8), runnable -> {
                    Thread thread = new Thread(runnable, "web-settings-client");
                    thread.setDaemon(true);
                    return thread;
                });
        ThreadPoolExecutor executor = workers;
        Thread worker = new Thread(() -> serve(listening, executor), "web-settings");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        ServerSocket listening = server;
        server = null;
        close(listening);
        if (workers != null) workers.shutdownNow();
        for (Socket client : clients) close(client);
        clients.clear();
    }

    public int getPort() {
        ServerSocket listening = server;
        return listening == null ? -1 : listening.getLocalPort();
    }

    private void serve(ServerSocket listening, ThreadPoolExecutor executor) {
        try {
            while (!listening.isClosed()) {
                Socket client = listening.accept();
                clients.add(client);
                if (listening.isClosed()) {
                    clients.remove(client);
                    close(client);
                    break;
                }
                try {
                    executor.execute(() -> {
                        try (Socket connection = client) {
                            connection.setSoTimeout(3000);
                            handle(connection);
                            connection.shutdownOutput();
                            int queued = Math.min(connection.getInputStream().available(), 8192);
                            if (queued > 0) connection.getInputStream().read(new byte[queued]);
                        } catch (IOException ignored) {
                            // Client disconnects and timeouts leave the listener available.
                        } finally {
                            clients.remove(client);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    clients.remove(client);
                    close(client);
                }
            }
        } catch (IOException ignored) {
            // Closing the listener interrupts accept().
        } finally {
            synchronized (this) {
                if (server == listening) server = null;
            }
            close(listening);
            executor.shutdownNow();
            for (Socket client : clients) close(client);
        }
    }

    private void handle(Socket client) throws IOException {
        InputStream input = new BufferedInputStream(client.getInputStream(), 16384);
        OutputStream output = client.getOutputStream();
        try {
            long deadline = System.nanoTime() + 3000000000L;
            String[] request = line(input, 2048, deadline).split(" ");
            if (request.length != 3 || !"HTTP/1.1".equals(request[2])) {
                throw new IllegalArgumentException();
            }
            Map<String, String> headers = new HashMap<>();
            int total = 0;
            while (true) {
                String line = line(input, 4096, deadline);
                total += line.length() + 2;
                if (total > 8192) throw new IllegalArgumentException();
                if (line.isEmpty()) break;
                int colon = line.indexOf(':');
                if (colon <= 0) throw new IllegalArgumentException();
                String key = line.substring(0, colon).toLowerCase(Locale.ROOT);
                if (headers.put(key, line.substring(colon + 1).trim()) != null) {
                    throw new IllegalArgumentException();
                }
            }
            String host = headers.get("host");
            String address = client.getLocalAddress().getHostAddress();
            // Require a literal address of this device; do not accept arbitrary DNS hosts.
            if (address.contains(":")) address = "[" + address + "]";
            String expectedHost = address + ":" + client.getLocalPort();
            if (!expectedHost.equals(host)) {
                error(output, 403, "请使用设备显示的网址访问");
                return;
            }
            String origin = headers.get("origin");
            if ((origin != null && !("http://" + host).equals(origin))
                    || "cross-site".equals(headers.get("sec-fetch-site"))) {
                error(output, 403, "不允许跨站请求");
                return;
            }
            String method = request[0];
            String path = request[1];
            if (headers.containsKey("transfer-encoding")) {
                error(output, 400, "不支持分块传输");
                return;
            }
            if ("/api/images/random".equals(path.split("\\?", 2)[0]) && images != null) {
                if (!"GET".equals(method)) {
                    error(output, 405, "请求方法不支持");
                    return;
                }
                randomImage(path, output);
                return;
            }
            if ("/api/uploads".equals(path) && uploads != null) {
                if ("GET".equals(method)) {
                    json(output, 200, uploads.status());
                } else if ("POST".equals(method)) {
                    long length = Long.parseLong(headers.get("content-length"));
                    if (length <= 0 || length > MAX_UPLOAD_BYTES) {
                        error(output, 413, "单张图片大小必须大于 0 且不超过 50 MiB");
                        return;
                    }
                    String encodedName = headers.get("x-file-name");
                    if (encodedName == null) throw new IllegalArgumentException();
                    String name = URLDecoder.decode(encodedName, "UTF-8");
                    if (!uploadSlot.tryAcquire()) {
                        error(output, 429, "设备正在接收其他图片，请稍后重试");
                        return;
                    }
                    try {
                        client.setSoTimeout(15000);
                        long uploadDeadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
                        InputStream bounded = new InputStream() {
                            private long remaining = length;
                            @Override public int read() throws IOException {
                                byte[] one = new byte[1];
                                int count = read(one, 0, 1);
                                return count == -1 ? -1 : one[0] & 255;
                            }
                            @Override public int read(byte[] buffer, int offset, int count) throws IOException {
                                if (System.nanoTime() > uploadDeadline) throw new IOException("图片上传超时");
                                if (remaining == 0) return -1;
                                int received = input.read(buffer, offset, (int) Math.min(count, remaining));
                                if (received == -1) throw new IOException("图片传输中断，未保存");
                                remaining -= received;
                                return received;
                            }
                        };
                        json(output, 200, uploads.receive(name, length, bounded));
                    } catch (IOException | SecurityException | IllegalArgumentException e) {
                        error(output, 400, e.getMessage() == null ? "图片保存失败" : e.getMessage());
                    } finally {
                        uploadSlot.release();
                    }
                } else {
                    error(output, 405, "请求方法不支持");
                }
                return;
            }
            byte[] body = new byte[0];
            if (headers.containsKey("content-length")) {
                int length = Integer.parseInt(headers.get("content-length"));
                if (length < 0 || length > 1024) throw new IllegalArgumentException();
                body = new byte[length];
                for (int i = 0; i < length; i++) {
                    if (System.nanoTime() > deadline) throw new IllegalArgumentException();
                    int next = input.read();
                    if (next == -1) throw new IllegalArgumentException();
                    body[i] = (byte) next;
                }
            }
            if ("/".equals(path) && "GET".equals(method)) {
                respond(output, 200, "text/html; charset=utf-8", page);
                return;
            }
            if (!"/api/settings".equals(path)) {
                error(output, 404, "页面不存在");
                return;
            }
            if ("GET".equals(method)) {
                json(output, 200, store.read());
            } else if ("POST".equals(method)) {
                if (!"application/json".equalsIgnoreCase(headers.get("content-type"))
                        || headers.containsKey("transfer-encoding")) {
                    error(output, 400, "需要 JSON 请求");
                    return;
                }
                saveBody(body);
                json(output, 200, store.read());
            } else {
                error(output, 405, "请求方法不支持");
            }
        } catch (RuntimeException e) {
            error(output, 400, "设置格式或取值无效，未保存");
        }
    }

    private void randomImage(String path, OutputStream output) throws IOException {
        String orientation;
        ImageResolution resolution;
        try {
            orientation = ImageOrientation.parse(path);
            resolution = ImageResolution.parse(path);
        } catch (IllegalArgumentException e) {
            error(output, 400, "orientation 仅支持 portrait 或 landscape；width 和 height 须成对提供，"
                    + "为 1–4096 的整数且总像素不超过 8388608");
            return;
        }
        if (resolution != null && !cropSlot.tryAcquire()) {
            error(output, 429, "设备正在处理其他图片，请稍后重试");
            return;
        }
        try {
            Image image;
            try {
                image = images.random(orientation);
                if (image != null && resolution != null) {
                    try (Image source = image) {
                        image = images.resize(source, resolution.width, resolution.height);
                    }
                    if (image == null) throw new IOException("图片处理失败");
                }
            } catch (SecurityException e) {
                error(output, 403, "没有读取图库的权限");
                return;
            } catch (IOException | RuntimeException e) {
                error(output, 500, "无法读取或处理图片，请稍后重试");
                return;
            }
            if (image == null) {
                error(output, 404, "没有符合方向的可读取图片");
                return;
            }
            try (Image response = image) {
                writeHeaders(output, 200, response.mime, -1);
                byte[] buffer = new byte[32768];
                int count;
                while ((count = response.input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("服务已停止");
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
        } finally {
            if (resolution != null) cropSlot.release();
        }
    }

    private void saveBody(byte[] body) {
        long interval = -1;
        String mode = null;
        boolean hasInterval = false;
        try (JsonReader reader = new JsonReader(new StringReader(new String(body, StandardCharsets.UTF_8)))) {
            reader.setLenient(false);
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("interval".equals(name) && !hasInterval && reader.peek() == JsonToken.NUMBER) {
                    interval = new BigDecimal(reader.nextString()).longValueExact();
                    hasInterval = true;
                } else if ("displayMode".equals(name) && mode == null && reader.peek() == JsonToken.STRING) {
                    mode = reader.nextString();
                } else {
                    throw new IllegalArgumentException();
                }
            }
            reader.endObject();
            if (!hasInterval || mode == null || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        store.save(interval, mode);
    }

    private static String line(InputStream input, int limit, long deadline) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (bytes.size() <= limit) {
            if (System.nanoTime() > deadline) throw new IOException("Request timeout");
            int next = input.read();
            if (next == -1) throw new IOException("Unexpected end of request");
            if (next == '\n') {
                byte[] value = bytes.toByteArray();
                if (value.length == 0 || value[value.length - 1] != '\r') throw new IOException("Invalid line");
                return new String(value, 0, value.length - 1, StandardCharsets.US_ASCII);
            }
            bytes.write(next);
        }
        throw new IOException("Request line too long");
    }

    private void error(OutputStream output, int status, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("error", message);
        json(output, status, body);
    }

    private void json(OutputStream output, int status, JsonObject body) throws IOException {
        respond(output, status, "application/json; charset=utf-8", gson.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(OutputStream output, int status, String type, byte[] body) throws IOException {
        writeHeaders(output, status, type, body.length);
        output.write(body);
        output.flush();
    }

    private static void writeHeaders(OutputStream output, int status, String type, long length) throws IOException {
        String headers = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Error") + "\r\n"
                + "Content-Type: " + type + (length >= 0 ? "\r\nContent-Length: " + length : "")
                + "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff"
                + "\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer"
                + "\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; "
                + "style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; form-action 'none'\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
    }

    private static void close(java.io.Closeable resource) {
        if (resource != null) {
            try { resource.close(); } catch (IOException ignored) { }
        }
    }
}
