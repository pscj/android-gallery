package com.netflixbar.gallery;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

public class SettingsDialog {

    private static final String PREFS_NAME = "gallery_settings";
    private static final String KEY_CHANGE_INTERVAL = "change_interval";
    private static final long DEFAULT_INTERVAL = 1000 * 60 * 5; // 5 minutes

    // Predefined time intervals in milliseconds
    private static final long[] INTERVALS = {
            1000 * 10,           // 10 seconds
            1000 * 30,           // 30 seconds
            1000 * 60,           // 1 minute
            1000 * 60 * 2,       // 2 minutes
            1000 * 60 * 5,       // 5 minutes
            1000 * 60 * 10,      // 10 minutes
            1000 * 60 * 15,      // 15 minutes
            1000 * 60 * 30,      // 30 minutes
            1000 * 60 * 60,      // 1 hour
    };

    private static final String[] INTERVAL_LABELS = {
            "10 秒",
            "30 秒",
            "1 分钟",
            "2 分钟",
            "5 分钟",
            "10 分钟",
            "15 分钟",
            "30 分钟",
            "1 小时",
    };

    private Context context;
    private SharedPreferences preferences;
    private OnSettingsSavedListener listener;

    public interface OnSettingsSavedListener {
        void onSettingsSaved(long newInterval);
    }

    public SettingsDialog(Context context, OnSettingsSavedListener listener) {
        this.context = context;
        this.listener = listener;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
        android.view.View view = inflater.inflate(R.layout.dialog_settings, null);

        Spinner spinnerInterval = view.findViewById(R.id.spinner_interval);
        TextView tvCurrentInterval = view.findViewById(R.id.tv_current_interval);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnSave = view.findViewById(R.id.btn_save);

        // Setup spinner adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, INTERVAL_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInterval.setAdapter(adapter);

        // Load current settings and set spinner selection
        long currentInterval = getChangeInterval();
        int currentPosition = getPositionForInterval(currentInterval);
        spinnerInterval.setSelection(currentPosition);
        updateCurrentIntervalText(tvCurrentInterval, currentInterval);

        AlertDialog dialog = builder.setView(view).create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            int selectedPosition = spinnerInterval.getSelectedItemPosition();
            long newInterval = INTERVALS[selectedPosition];

            saveChangeInterval(newInterval);
            if (listener != null) {
                listener.onSettingsSaved(newInterval);
            }
            dialog.dismiss();
            android.widget.Toast.makeText(context, "设置已保存", android.widget.Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private int getPositionForInterval(long interval) {
        for (int i = 0; i < INTERVALS.length; i++) {
            if (INTERVALS[i] == interval) {
                return i;
            }
        }
        return 4; // Default to 10 minutes
    }

    private void updateCurrentIntervalText(TextView tv, long interval) {
        String label = INTERVAL_LABELS[getPositionForInterval(interval)];
        String text = "当前时间间隔：" + label;
        tv.setText(text);
    }

    public long getChangeInterval() {
        return preferences.getLong(KEY_CHANGE_INTERVAL, DEFAULT_INTERVAL);
    }

    public void saveChangeInterval(long interval) {
        preferences.edit().putLong(KEY_CHANGE_INTERVAL, interval).apply();
    }
}
