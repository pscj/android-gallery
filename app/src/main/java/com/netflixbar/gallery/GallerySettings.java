package com.netflixbar.gallery;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared storage for the native dialog, playback and the web API. */
public final class GallerySettings {
    public static final String PREFS_NAME = "gallery_settings";
    public static final String KEY_INTERVAL = "change_interval";
    public static final String KEY_MODE = "display_mode";
    public static final String GRID = "grid";
    public static final String SINGLE = "single";
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

    public static boolean isValid(long interval, String mode) {
        boolean found = false;
        for (long value : INTERVALS) {
            if (value == interval) found = true;
        }
        return found && (GRID.equals(mode) || SINGLE.equals(mode));
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
