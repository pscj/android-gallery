package com.netflixbar.gallery;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import static org.junit.Assert.*;

public class UploadPolicyTest {
    @Test public void namesStayInsideDestinationAndPreserveUnicode() {
        String first = UploadPolicy.distinctName("旅行 照片.JPG");
        String second = UploadPolicy.distinctName("旅行 照片.JPG");
        assertTrue(first.startsWith("旅行 照片_"));
        assertTrue(first.endsWith(".jpg"));
        assertNotEquals(first, second);
        for (String name : new String[]{"../a.jpg", "folder/a.png", "C:\\a.png", ".hidden.png", "a\n.jpg", "a.exe", "no-extension"}) {
            try { UploadPolicy.distinctName(name); fail(name); } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void copyStreamsWithBoundedReadsAndDoesNotConsumeNextRequest() throws IOException {
        byte[] bytes = new byte[100003];
        Arrays.fill(bytes, (byte) 12);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        InputStream capped = new InputStream() {
            @Override public int read() { return input.read(); }
            @Override public int read(byte[] data, int offset, int length) {
                assertTrue(length <= 32768);
                return input.read(data, offset, length);
            }
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GalleryImageUploads.copy(capped, output, 100000);
        assertEquals(100000, output.size());
        assertEquals(3, input.available());
    }

    @Test public void interruptedBodyFailsInsteadOfPublishingPartialData() throws IOException {
        try {
            GalleryImageUploads.copy(new ByteArrayInputStream(new byte[3]), new ByteArrayOutputStream(), 10);
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("中断"));
        }
    }

    @Test public void onlySupportedImageMimeTypesAreAccepted() {
        for (String mime : new String[]{"image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp"}) {
            assertTrue(UploadPolicy.supportsMime(mime));
        }
        assertFalse(UploadPolicy.supportsMime("image/svg+xml"));
        assertFalse(UploadPolicy.supportsMime("text/plain"));
        assertFalse(UploadPolicy.supportsMime(null));
    }
}
