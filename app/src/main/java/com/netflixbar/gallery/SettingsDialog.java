package com.netflixbar.gallery;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.netflixbar.gallery.databinding.DialogSettingsBinding;

public class SettingsDialog {

    private final Context context;
    private final GallerySettings settings;
    private final WebSettingsController webSettings;
    private AlertDialog dialog;

    public SettingsDialog(Context context, WebSettingsController webSettings) {
        this.context = context;
        this.settings = new GallerySettings(context);
        this.webSettings = webSettings;
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
        DialogSettingsBinding binding = DialogSettingsBinding.inflate(inflater);
        android.view.View view = binding.getRoot();

        Spinner spinnerInterval = binding.spinnerInterval;
        RadioGroup radioGroupDisplayMode = binding.radioGroupDisplayMode;
        RadioButton radioGridMode = binding.radioGridMode;
        RadioButton radioSingleMode = binding.radioSingleMode;
        TextView tvCurrentInterval = binding.tvCurrentInterval;
        Button btnCancel = binding.btnCancel;
        Button btnSave = binding.btnSave;

        // Setup spinner adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, GallerySettings.labels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInterval.setAdapter(adapter);

        // Load current settings and set spinner selection
        long currentInterval = settings.getInterval();
        int currentPosition = getPositionForInterval(currentInterval);
        spinnerInterval.setSelection(currentPosition);
        updateCurrentIntervalText(tvCurrentInterval, currentInterval);

        String currentMode = settings.getMode();
        if (GallerySettings.SINGLE.equals(currentMode)) {
            radioSingleMode.setChecked(true);
        } else {
            radioGridMode.setChecked(true);
        }

        androidx.appcompat.widget.SwitchCompat webSwitch = binding.switchWebSettings;
        TextView webStatus = binding.tvWebStatus;
        webSwitch.setChecked(webSettings.isEnabled());
        webStatus.setText(webSettings.getStatus());
        boolean[] updatingSwitch = {false};
        webSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (updatingSwitch[0]) return;
            if (enabled) webSettings.enable(); else webSettings.disable();
            updatingSwitch[0] = true;
            webSwitch.setChecked(webSettings.isEnabled());
            updatingSwitch[0] = false;
            webStatus.setText(webSettings.getStatus());
        });
        dialog = builder.setView(view).create();
        android.os.Handler statusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable refreshStatus = new Runnable() {
            @Override public void run() {
                webStatus.setText(webSettings.getStatus());
                updateCurrentIntervalText(tvCurrentInterval, settings.getInterval());
                statusHandler.postDelayed(this, 2000);
            }
        };
        dialog.setOnDismissListener(ignored -> statusHandler.removeCallbacks(refreshStatus));
        statusHandler.post(refreshStatus);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            int selectedPosition = spinnerInterval.getSelectedItemPosition();
            long newInterval = GallerySettings.intervals()[selectedPosition];
            int checkedId = radioGroupDisplayMode.getCheckedRadioButtonId();
            String newDisplayMode = checkedId == R.id.radio_single_mode ? GallerySettings.SINGLE : GallerySettings.GRID;

            settings.save(newInterval, newDisplayMode);
            dialog.dismiss();
            android.widget.Toast.makeText(context, "设置已保存", android.widget.Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private int getPositionForInterval(long interval) {
        for (int i = 0; i < GallerySettings.intervals().length; i++) {
            if (GallerySettings.intervals()[i] == interval) {
                return i;
            }
        }
        return 4; // 5 minutes
    }

    private void updateCurrentIntervalText(TextView tv, long interval) {
        String label = GallerySettings.labels()[getPositionForInterval(interval)];
        String text = "当前时间间隔：" + label;
        tv.setText(text);
    }

}
