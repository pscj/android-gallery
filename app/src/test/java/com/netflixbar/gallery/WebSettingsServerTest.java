package com.netflixbar.gallery;

import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class WebSettingsServerTest {
    private WebSettingsServer server;
    private long interval;
    private String mode;
    private int saves;

    @Before public void start() throws IOException {
        interval = GallerySettings.DEFAULT_INTERVAL;
        mode = GallerySettings.GRID;
        server = new WebSettingsServer(new WebSettingsServer.Store() {
            @Override public JsonObject read() {
                JsonObject result = new JsonObject();
                result.addProperty("interval", interval);
                result.addProperty("displayMode", mode);
                return result;
            }
            @Override public void save(long value, String displayMode) {
                if (!GallerySettings.isValid(value, displayMode)) throw new IllegalArgumentException();
                interval = value;
                mode = displayMode;
                saves++;
            }
        }, "<!doctype html><title>图库设置</title>".getBytes(StandardCharsets.UTF_8));
        server.start(0);
    }

    @After public void stop() { server.stop(); }

    private String request(String method, String path, String headers, String body) throws IOException {
        return raw(method + " " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + server.getPort()
                + "\r\n" + headers + "Connection: close\r\n\r\n" + body);
    }

    private String raw(String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(6000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int count;
            while ((count = socket.getInputStream().read(buffer)) != -1) response.write(buffer, 0, count);
            return new String(response.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String post(String body, String extraHeaders) throws IOException {
        return request("POST", "/api/settings", "Content-Type: application/json\r\nContent-Length: "
                + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" + extraHeaders, body);
    }

    @Test public void servesPageAndReadsSettingsWithoutPassword() throws IOException {
        String page = request("GET", "/", "", "");
        assertTrue(page.startsWith("HTTP/1.1 200"));
        assertTrue(page.contains("图库设置"));
        assertTrue(page.contains("Cache-Control: no-store"));
        assertTrue(request("GET", "/api/settings", "", "").contains("\"interval\":300000"));
    }

    @Test public void persistsValidSettingsAndReadsThemBack() throws IOException {
        assertTrue(post("{\"interval\":10000,\"displayMode\":\"single\"}", "").startsWith("HTTP/1.1 200"));
        assertEquals(1, saves);
        assertTrue(request("GET", "/api/settings", "", "").contains("\"displayMode\":\"single\""));
        assertEquals(10000, interval);
    }

    @Test public void rejectsInvalidValuesWithoutPartialSave() throws IOException {
        String[] bodies = {"{}", "broken", "{interval:10000,displayMode:'grid'}",
                "{\"interval\":10000,\"interval\":30000,\"displayMode\":\"grid\"}",
                "{\"interval\":10000,\"displayMode\":\"grid\"} trailing", "{\"interval\":10001,\"displayMode\":\"grid\"}",
                "{\"interval\":10000,\"displayMode\":\"unknown\"}",
                "{\"interval\":10000.5,\"displayMode\":\"grid\"}",
                "{\"interval\":\"10000\",\"displayMode\":\"grid\"}",
                "{\"interval\":null,\"displayMode\":\"grid\"}",
                "{\"interval\":10000,\"displayMode\":\"grid\",\"extra\":1}"};
        for (String body : bodies) assertTrue(body, post(body, "").startsWith("HTTP/1.1 400"));
        assertEquals(0, saves);
        assertEquals(300000, interval);
        assertEquals("grid", mode);
    }

    @Test public void rejectsCrossOriginAndRebindingRequests() throws IOException {
        String body = "{\"interval\":10000,\"displayMode\":\"single\"}";
        assertTrue(post(body, "Origin: http://attacker.invalid\r\n").startsWith("HTTP/1.1 403"));
        assertTrue(post(body, "Sec-Fetch-Site: cross-site\r\n").startsWith("HTTP/1.1 403"));
        assertTrue(raw("GET / HTTP/1.1\r\nHost: attacker.invalid\r\n\r\n").startsWith("HTTP/1.1 403"));
        assertTrue(post(body, "Origin: http://127.0.0.1:" + server.getPort() + "\r\n").startsWith("HTTP/1.1 200"));
        assertEquals(1, saves);
    }

    @Test public void rejectsUnsupportedRoutesMethodsAndOversizedBodies() throws IOException {
        assertTrue(request("GET", "/../secret", "", "").startsWith("HTTP/1.1 404"));
        assertTrue(request("DELETE", "/api/settings", "", "").startsWith("HTTP/1.1 405"));
        assertTrue(request("POST", "/api/settings", "Content-Type: application/json\r\nContent-Length: 999999\r\n", "").startsWith("HTTP/1.1 400"));
        assertTrue(post("{}", "Transfer-Encoding: chunked\r\n").startsWith("HTTP/1.1 400"));
        assertTrue(raw("GET / HTTP/1.1\r\nHost: a\r\nHost: b\r\n\r\n").startsWith("HTTP/1.1 400"));
        assertEquals(0, saves);
    }

    @Test public void timesOutIncompleteClientAndContinuesServing() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(6000);
            socket.getOutputStream().write("GET / HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
            assertEquals(-1, socket.getInputStream().read());
        }
        assertTrue(request("GET", "/", "", "").startsWith("HTTP/1.1 200"));
    }

    @Test public void stopClosesListenerAndReleasesPort() throws IOException {
        int port = server.getPort();
        server.stop();
        assertEquals(-1, server.getPort());
        try (ServerSocket replacement = new ServerSocket(port)) {
            assertEquals(port, replacement.getLocalPort());
        }
    }

    @Test public void portConflictIsReported() throws IOException {
        WebSettingsServer second = new WebSettingsServer(null, new byte[0]);
        try {
            second.start(server.getPort());
            fail("An occupied port must fail");
        } catch (IOException expected) {
            assertEquals(-1, second.getPort());
        } finally { second.stop(); }
    }
}
