package com.netflixbar.gallery;

import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class WebImageUploadTest {
    private WebSettingsServer server;
    private volatile String receivedName;
    private volatile byte[] received;
    private final CountDownLatch receiving = new CountDownLatch(1);

    @Before public void start() throws IOException {
        server = new WebSettingsServer(new WebSettingsServer.Store() {
            @Override public JsonObject read() { return new JsonObject(); }
            @Override public void save(long interval, String mode) { }
        }, new byte[0], new WebSettingsServer.Uploads() {
            @Override public JsonObject status() {
                JsonObject status = new JsonObject();
                status.addProperty("directory", "/storage/card/Pictures/GalleryUploads");
                status.addProperty("available", true);
                return status;
            }
            @Override public JsonObject receive(String name, long length, InputStream input) throws IOException {
                UploadPolicy.extension(name);
                receivedName = name;
                receiving.countDown();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                GalleryImageUploads.copy(input, output, length);
                received = output.toByteArray();
                JsonObject result = new JsonObject();
                result.addProperty("indexed", true);
                return result;
            }
        });
        server.start(0);
    }

    @After public void stop() { server.stop(); }

    private Socket open(String path, String method, String extra) throws IOException {
        Socket socket = new Socket("127.0.0.1", server.getPort());
        socket.setSoTimeout(5000);
        socket.getOutputStream().write((method + " " + path + " HTTP/1.1\r\nHost: 127.0.0.1:"
                + server.getPort() + "\r\n" + extra + "\r\n").getBytes(StandardCharsets.US_ASCII));
        return socket;
    }

    private String response(Socket socket) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int count;
        while ((count = socket.getInputStream().read(buffer)) != -1) bytes.write(buffer, 0, count);
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test public void exposesDestinationAndAcceptsEncodedUnicodeNames() throws IOException {
        try (Socket socket = open("/api/uploads", "GET", "")) {
            assertTrue(response(socket).contains("Pictures/GalleryUploads"));
        }
        try (Socket socket = open("/api/uploads", "POST", "Content-Length: 4\r\nX-File-Name: %E7%85%A7%E7%89%87.jpg\r\n")) {
            socket.getOutputStream().write(new byte[]{1, 2, 3, 4});
            assertTrue(response(socket).startsWith("HTTP/1.1 200"));
            assertEquals("照片.jpg", receivedName);
            assertArrayEquals(new byte[]{1, 2, 3, 4}, received);
        }
    }

    @Test public void rejectsOversizedCrossOriginAndPathTraversalRequests() throws IOException {
        try (Socket socket = open("/api/uploads", "POST", "Content-Length: 52428801\r\nX-File-Name: test.jpg\r\n")) {
            assertTrue(response(socket).startsWith("HTTP/1.1 413"));
        }
        try (Socket socket = open("/api/uploads", "POST", "Content-Length: 4\r\nOrigin: http://other.invalid\r\n")) {
            assertTrue(response(socket).startsWith("HTTP/1.1 403"));
        }
        try (Socket socket = open("/api/uploads", "POST", "Content-Length: 4\r\nX-File-Name: ..%2Ftest.jpg\r\n")) {
            assertTrue(response(socket).startsWith("HTTP/1.1 400"));
        }
        assertNull(received);
    }

    @Test public void settingsStayResponsiveDuringUploadAndConcurrentUploadIsBounded() throws Exception {
        try (Socket upload = open("/api/uploads", "POST", "Content-Length: 4\r\nX-File-Name: test.jpg\r\n")) {
            assertTrue(receiving.await(2, TimeUnit.SECONDS));
            try (Socket settings = open("/api/settings", "GET", "")) {
                assertTrue(response(settings).startsWith("HTTP/1.1 200"));
            }
            try (Socket other = open("/api/uploads", "POST", "Content-Length: 4\r\nX-File-Name: other.jpg\r\n")) {
                assertTrue(response(other).startsWith("HTTP/1.1 429"));
            }
            upload.getOutputStream().write(new byte[4]);
            assertTrue(response(upload).startsWith("HTTP/1.1 200"));
        }
    }

    @Test public void interruptedTransferFailsAndServiceRemainsAvailable() throws IOException {
        try (Socket socket = open("/api/uploads", "POST", "Content-Length: 4\r\nX-File-Name: test.jpg\r\n")) {
            socket.getOutputStream().write(new byte[2]);
            socket.shutdownOutput();
            assertTrue(response(socket).startsWith("HTTP/1.1 400"));
        }
        assertNull(received);
        try (Socket socket = open("/api/settings", "GET", "")) {
            assertTrue(response(socket).startsWith("HTTP/1.1 200"));
        }
    }

    @Test public void disablingServiceClosesActiveUploads() throws Exception {
        try (Socket socket = open("/api/uploads", "POST", "Content-Length: 4\r\nX-File-Name: test.jpg\r\n")) {
            assertTrue(receiving.await(2, TimeUnit.SECONDS));
            server.stop();
            assertEquals(-1, socket.getInputStream().read());
            assertNull(received);
        }
    }
}
