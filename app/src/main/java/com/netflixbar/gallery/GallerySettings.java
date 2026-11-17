package com.netflixbar.gallery;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Random;

/** Shared storage for the native dialog, playback and the web API. */
public final class GallerySettings {
    public static final String PREFS_NAME = "gallery_settings";
    public static final String KEY_INTERVAL = "change_interval";
    public static final String KEY_MODE = "display_mode";
    public static final String GRID = "grid";
    public static final String SINGLE = "single";
    public static final String TWO = "two";
    public static final String THREE = "three";
    public static final String RANDOM = "random";
    private static final String[] MODES = {RANDOM, SINGLE, TWO, THREE, GRID};
    private static final String[] MODE_LABELS = {"随机", "单图", "双图", "三图", "四图"};
    public static final long DEFAULT_INTERVAL = 300000L;
    private static final long[] INTERVALS = {
            10000, 30000, 60000, 120000, 300000, 600000, 900000, 1800000, 3600000
    };
    private static final String[] LABELS = {
            "10 秒", "30 秒", "1 分钟", "2 分钟", "5 分钟", "10 分钟", "15 分钟", "30 分钟", "1 小时"
    };
    private final SharedPreferences preferences;

    public GallerySettings(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE));
    }

    GallerySettings(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public static long[] intervals() { return INTERVALS.clone(); }
    public static String[] labels() { return LABELS.clone(); }
    public static String[] modes() { return MODES.clone(); }
    public static String[] modeLabels() { return MODE_LABELS.clone(); }

    public static int selectCount(String mode, Random random) {
        if (SINGLE.equals(mode)) return 1;
        if (TWO.equals(mode)) return 2;
        if (THREE.equals(mode)) return 3;
        if (GRID.equals(mode)) return 4;
        if (RANDOM.equals(mode)) return random.nextInt(4) + 1;
        throw new IllegalArgumentException("不支持的显示模式");
    }

    public static boolean isValid(long interval, String mode) {
        boolean found = false;
        for (long value : INTERVALS) {
            if (value == interval) found = true;
        }
        boolean validMode = false;
        for (String value : MODES) {
            if (value.equals(mode)) validMode = true;
        }
        return found && validMode;
    }

    public long getInterval() {
        return preferences.getLong(KEY_INTERVAL, DEFAULT_INTERVAL);
    }

    public String getMode() {
        return preferences.getString(KEY_MODE, GRID);
    }

    public void save(long interval, String mode) {
        if (!isValid(interval, mode)) throw new IllegalArgumentException("不支持的设置值");
        preferences.edit().putLong(KEY_INTERVAL, interval).putString(KEY_MODE, mode).apply();
    }

    public void register(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregister(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
