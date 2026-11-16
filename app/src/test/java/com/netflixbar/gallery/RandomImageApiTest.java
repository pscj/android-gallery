package com.netflixbar.gallery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class RandomImageApiTest {
    private WebSettingsServer server;
    private String requested;
    private int calls;
    private boolean closed;
    private int failure;
    private int outputWidth;
    private int outputHeight;
    private java.util.concurrent.CountDownLatch entered;
    private java.util.concurrent.CountDownLatch release;
    private final byte[] image = {(byte) 0xff, (byte) 0xd8, 0, 13, 10, (byte) 0xff, (byte) 0xd9};

    @Before public void start() throws IOException {
        server = new WebSettingsServer(new WebSettingsServer.Store() {
            @Override public com.google.gson.JsonObject read() { return new com.google.gson.JsonObject(); }
            @Override public void save(long interval, String mode) { }
        }, new byte[0], null, new WebSettingsServer.Images() {
            @Override public WebSettingsServer.Image random(String orientation) throws IOException {
            requested = orientation;
            calls++;
            if (failure == 404) return null;
            if (failure == 403) throw new SecurityException();
            if (failure == 500) throw new IOException();
            if (failure == 501) throw new IllegalStateException();
            return new WebSettingsServer.Image(new ByteArrayInputStream(image) {
                @Override public void close() { closed = true; }
            }, "image/jpeg");
            }

            @Override public WebSettingsServer.Image resize(WebSettingsServer.Image source, int width, int height)
                    throws IOException {
                outputWidth = width;
                outputHeight = height;
                if (entered != null) {
                    entered.countDown();
                    try {
                        if (!release.await(3, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("timeout");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                }
                if (failure == 502) throw new IOException("Conversion failed");
                return new WebSettingsServer.Image(new ByteArrayInputStream(image), "image/jpeg");
            }
        });
        server.start(0);
    }

    @After public void stop() { server.stop(); }

    private byte[] request(String method, String query, String extra) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(6000);
            String request = method + " /api/images/random" + query + " HTTP/1.1\r\nHost: 127.0.0.1:"
                    + server.getPort() + "\r\n" + extra + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = socket.getInputStream().read(buffer)) != -1) response.write(buffer, 0, count);
            return response.toByteArray();
        }
    }

    private String get(String query) throws IOException {
        return new String(request("GET", query, ""), StandardCharsets.ISO_8859_1);
    }

    @Test public void defaultsToLandscapeAndStreamsExactBytes() throws IOException {
        byte[] response = request("GET", "", "");
        String text = new String(response, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("HTTP/1.1 200"));
        assertTrue(text.contains("Content-Type: image/jpeg\r\n"));
        assertTrue(text.contains("Cache-Control: no-store\r\n"));
        int body = text.indexOf("\r\n\r\n") + 4;
        assertArrayEquals(image, java.util.Arrays.copyOfRange(response, body, response.length));
        assertEquals("landscape", requested);
        assertTrue(closed);
    }

    @Test public void acceptsBothDirectionsAndEncodedParameter() throws IOException {
        for (String direction : new String[]{"portrait", "landscape"}) {
            assertTrue(get("?orientation=" + direction).startsWith("HTTP/1.1 200"));
            assertEquals(direction, requested);
        }
        assertTrue(get("?%6frientation=%70ortrait").startsWith("HTTP/1.1 200"));
        assertEquals("portrait", requested);
    }

    @Test public void rejectsInvalidAndDuplicateDirectionsBeforeQuerying() throws IOException {
        for (String query : new String[]{"?orientation=", "?orientation", "?orientation=other",
                "?orientation=Portrait", "?orientation=%", "?orientation=portrait&orientation=landscape",
                "?orientation=portrait&%6frientation=portrait"}) {
            assertTrue(query, get(query).startsWith("HTTP/1.1 400"));
        }
        assertEquals(0, calls);
    }

    @Test public void reportsNoMatchPermissionAndServerErrorsAsJson() throws IOException {
        for (int status : new int[]{404, 403, 500, 501}) {
            failure = status;
            String response = get("");
            assertTrue(response.startsWith("HTTP/1.1 " + (status == 501 ? 500 : status)));
            assertTrue(response.contains("Content-Type: application/json; charset=utf-8"));
            assertTrue(response.contains("\"error\":"));
        }
    }

    @Test public void rejectsOtherMethodsAndCrossOriginRequests() throws IOException {
        assertTrue(new String(request("POST", "", ""), StandardCharsets.US_ASCII).startsWith("HTTP/1.1 405"));
        assertTrue(new String(request("GET", "", "Origin: http://example.invalid\r\n"),
                StandardCharsets.US_ASCII).startsWith("HTTP/1.1 403"));
        assertEquals(0, calls);
    }

    @Test public void classifiesExifRotationSquaresAndInvalidDimensions() {
        for (int exif = 1; exif <= 8; exif++) {
            boolean swapped = exif >= 5;
            assertEquals(swapped, ImageOrientation.matches("portrait", 800, 600, exif));
            assertEquals(!swapped, ImageOrientation.matches("landscape", 800, 600, exif));
            assertTrue(ImageOrientation.matches("landscape", 600, 600, exif));
            assertFalse(ImageOrientation.matches("portrait", 600, 600, exif));
        }
        assertFalse(ImageOrientation.matches("landscape", 0, 600, 1));
        assertFalse(ImageOrientation.matches("portrait", 600, -1, 1));
    }

    @Test public void passesValidatedResolutionAndKeepsDefaultDirection() throws IOException {
        assertTrue(get("?width=1920&height=1080").startsWith("HTTP/1.1 200"));
        assertEquals("landscape", requested);
        assertEquals(1920, outputWidth);
        assertEquals(1080, outputHeight);
        assertTrue(closed);
        assertTrue(get("?orientation=portrait&%77idth=1080&height=1920").startsWith("HTTP/1.1 200"));
        assertEquals("portrait", requested);
        assertEquals(1080, outputWidth);
        assertEquals(1920, outputHeight);
        assertTrue(get("?width=4096&height=2048").startsWith("HTTP/1.1 200"));
        assertTrue(get("?width=1&height=1").startsWith("HTTP/1.1 200"));
    }

    @Test public void rejectsBadResolutionBeforeSelectingAnImage() throws IOException {
        for (String query : new String[]{"?width=100", "?height=100", "?width=&height=100",
                "?width=10&height", "?width=0&height=100", "?width=-1&height=100",
                "?width=1.5&height=100", "?width=1e3&height=100", "?width=+1&height=100",
                "?width=4097&height=100", "?width=4096&height=2049", "?width=100&height=0",
                "?width=999999999999999999999999&height=1", "?width=1&width=2&height=2",
                "?width=1&height=1&%68eight=2", "?width=%&height=100"}) {
            assertTrue(query, get(query).startsWith("HTTP/1.1 400"));
        }
        assertEquals(0, calls);
    }

    @Test public void conversionFailureClosesSourceAndReleasesSlot() throws IOException {
        failure = 502;
        assertTrue(get("?width=100&height=100").startsWith("HTTP/1.1 500"));
        assertTrue(closed);
        failure = 0;
        assertTrue(get("?width=100&height=100").startsWith("HTTP/1.1 200"));
    }

    @Test public void busyCropDoesNotBlockOriginalImagesOrSettings() throws Exception {
        entered = new java.util.concurrent.CountDownLatch(1);
        release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<String> first = executor.submit(() -> get("?width=100&height=100"));
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(get("?width=100&height=100").startsWith("HTTP/1.1 429"));
            assertTrue(get("").startsWith("HTTP/1.1 200"));
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                socket.setSoTimeout(2000);
                socket.getOutputStream().write(("GET /api/settings HTTP/1.1\r\nHost: 127.0.0.1:"
                        + server.getPort() + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                assertEquals("HTTP/1.1 200 OK", new java.io.BufferedReader(new java.io.InputStreamReader(
                        socket.getInputStream(), StandardCharsets.US_ASCII)).readLine());
            }
            release.countDown();
            assertTrue(first.get(3, java.util.concurrent.TimeUnit.SECONDS).startsWith("HTTP/1.1 200"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
