package com.netflixbar.gallery;

import android.content.SharedPreferences;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import static org.junit.Assert.*;

public class GallerySettingsTest {
    private final Map<String, Object> persisted = new HashMap<>();
    private final Map<String, Object> pending = new HashMap<>();
    private int writes;

    private GallerySettings settings() {
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("put")) {
                        pending.put((String) args[0], args[1]);
                        return proxy;
                    }
                    if (method.getName().equals("apply")) {
                        persisted.putAll(pending);
                        pending.clear();
                        writes++;
                    }
                    return null;
                });
        SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("edit")) return editor;
                    if (method.getName().equals("getLong") || method.getName().equals("getString")) {
                        return persisted.containsKey(args[0]) ? persisted.get(args[0]) : args[1];
                    }
                    return null;
                });
        return new GallerySettings(preferences);
    }

    @Test public void defaultsAreSharedAndExistingPreferencesSurvive() {
        GallerySettings settings = settings();
        assertEquals(300000, settings.getInterval());
        assertEquals("grid", settings.getMode());
        persisted.put("change_interval", 600000L);
        persisted.put("display_mode", "single");
        assertEquals(600000, settings.getInterval());
        assertEquals("single", settings.getMode());
        assertEquals(0, writes);
    }

    @Test public void validPairIsSavedTogetherAndReadByNewInstance() {
        settings().save(10000, "single");
        assertEquals(1, writes);
        assertEquals(10000, settings().getInterval());
        assertEquals("single", settings().getMode());
    }

    @Test public void invalidPairDoesNotChangeEitherValue() {
        GallerySettings settings = settings();
        settings.save(30000, "single");
        try { settings.save(10001, "grid"); fail(); } catch (IllegalArgumentException expected) { }
        try { settings.save(10000, "bad"); fail(); } catch (IllegalArgumentException expected) { }
        assertEquals(1, writes);
        assertEquals(30000, settings.getInterval());
        assertEquals("single", settings.getMode());
    }

    @Test public void allNativeAndWebOptionsAreValid() {
        assertEquals(9, GallerySettings.intervals().length);
        assertEquals(GallerySettings.labels().length, GallerySettings.intervals().length);
        for (long interval : GallerySettings.intervals()) {
            for (String mode : GallerySettings.modes()) {
                assertTrue(GallerySettings.isValid(interval, mode));
            }
        }
        assertFalse(GallerySettings.isValid(-1, "grid"));
        assertFalse(GallerySettings.isValid(10000, null));
    }

    @Test public void allFiveModesPersistAcrossSettingsInstances() {
        assertArrayEquals(new String[]{"random", "single", "two", "three", "grid"}, GallerySettings.modes());
        assertEquals(GallerySettings.modes().length, GallerySettings.modeLabels().length);
        for (String mode : GallerySettings.modes()) {
            settings().save(300000, mode);
            assertEquals(mode, settings().getMode());
        }
    }

    @Test public void fixedModesNeverRandomizeTheirCount() {
        Random unexpectedRandom = new Random() {
            @Override public int nextInt(int bound) {
                throw new AssertionError("固定模式不应随机选择数量");
            }
        };
        assertEquals(1, GallerySettings.selectCount(GallerySettings.SINGLE, unexpectedRandom));
        assertEquals(2, GallerySettings.selectCount(GallerySettings.TWO, unexpectedRandom));
        assertEquals(3, GallerySettings.selectCount(GallerySettings.THREE, unexpectedRandom));
        assertEquals(4, GallerySettings.selectCount(GallerySettings.GRID, unexpectedRandom));
    }

    @Test public void randomModeCanSelectEveryCountFromOneThroughFour() {
        for (int value = 0; value < 4; value++) {
            final int draw = value;
            Random controlledRandom = new Random() {
                @Override public int nextInt(int bound) {
                    assertEquals(4, bound);
                    return draw;
                }
            };
            assertEquals(value + 1, GallerySettings.selectCount(GallerySettings.RANDOM, controlledRandom));
        }
    }
}
