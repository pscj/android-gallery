package com.netflixbar.gallery;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.hardware.display.DisplayManager;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.media.MediaMetadataRetriever;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Build;
import android.view.WindowManager.LayoutParams;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.MimeTypes;
import com.netflixbar.gallery.databinding.FragmentFirstBinding;
import com.netflixbar.gallery.entity.GPSResult;
import com.netflixbar.gallery.utils.FileUtils;
import com.netflixbar.gallery.utils.HttpUtils;
import com.netflixbar.gallery.utils.ImageUtils;
import com.netflixbar.gallery.utils.Utils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;

public class FirstFragment extends Fragment {

    private static final boolean ENABLE_VIDEO = true;

    private FragmentFirstBinding binding;

    private static final int GRID_COLUMNS = 2;
    private static final int GRID_ROWS = 2;
    private static final int MAX_VIDEO_SLOTS = 1;
    private static final int GRID_SLOT_COUNT = GRID_COLUMNS * GRID_ROWS;
    private static final int SINGLE_MODE_CACHE_COUNT = 12;
    private int PAGE_SIZE = 1;
    private long CHANGE_TIMER = GallerySettings.DEFAULT_INTERVAL;
    private static final int VIDEO_BUFFER_MS = 3000;
    private static final int VIDEO_BACKBUFFER_MS = 1000;
    private Handler handler = new Handler();
    private ActivityResultLauncher<String[]> storagePermissionLauncher;
    private final List<ExoPlayer> activePlayers = new ArrayList<>();
    private Gson gson = new Gson();
    private SettingsDialog settingsDialog;
    private GallerySettings gallerySettings;
    private final Runnable uploadRefreshRunnable = this::refreshUploadedMedia;
    private final Runnable applySettingsRunnable = this::applySavedSettings;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (preferences, key) -> {
        if (GallerySettings.KEY_INTERVAL.equals(key) || GallerySettings.KEY_MODE.equals(key)) {
            handler.removeCallbacks(applySettingsRunnable);
            handler.post(applySettingsRunnable);
        }
    };
    private static final String DISPLAY_MODE_GRID = GallerySettings.GRID;
    private static final String DISPLAY_MODE_SINGLE = GallerySettings.SINGLE;
    private GestureDetector gestureDetector;
    private boolean hasMediaContent = false;
    private String currentDisplayMode = DISPLAY_MODE_GRID;
    // 本轮展示独立于用户保存的模式，随机模式也可能展示单图。
    private boolean showingSingleMode = false;
    private final List<PicItem> singleModeItems = new ArrayList<>();
    private int singleModeIndex = 0;
    private View currentMultiLayout;
    private int currentLayoutResId = 0;
    private final List<MediaSlot> currentMediaSlots = new ArrayList<>();
    private final Random random = new Random();
    private DualPhotoAtmosphere dualPhotoAtmosphere;
    private MediaAtmosphereController mediaAtmosphere;
    private VideoPreparation videoPreparation;
    private Future<?> videoPreparationTask;
    private Future<?> landscapeTask, twoPhotoPrefetchTask;
    private boolean landscapePreparing;
    private int mediaGeneration;
    private TwoPhotoPrefetch twoPhotoPrefetch;
    private long nextRefreshDelayMs = CHANGE_TIMER;
    private boolean refreshScheduled = false;
    private final Runnable refreshRunnable = this::loadImage;
    private static final int[] IMAGE_VIEW_IDS = {R.id.imgview1, R.id.imgview2, R.id.imgview3, R.id.imgview4};
    private static final int MAX_RETRY_COUNT = 5;
    private static final long RETRY_DELAY_MS = 2000;
    private int loadRetryCount = 0;
    private BroadcastReceiver storageReceiver;
    private static final int[] VIDEO_VIEW_IDS = {R.id.videoView1, R.id.videoView2, R.id.videoView3, R.id.videoView4};
    private static final int[] VIDEO_INFO_IDS = {R.id.videoInfo1, R.id.videoInfo2, R.id.videoInfo3, R.id.videoInfo4};
    private static final int[] VIDEO_TITLE_IDS = {R.id.videoTitle1, R.id.videoTitle2, R.id.videoTitle3, R.id.videoTitle4};
    private static final int[] VIDEO_DETAILS_IDS = {R.id.videoDetails1, R.id.videoDetails2, R.id.videoDetails3, R.id.videoDetails4};
    private static final int[] IMAGE_LOCATION_IDS = {R.id.imageLocation1, R.id.imageLocation2, R.id.imageLocation3, R.id.imageLocation4};
    private static final int[] DEFAULT_SLOT_ORDER = {0, 1, 2, 3};

    class PicItem{
        PicItem(String path, int ori, String gpsStr, boolean isVideo, Uri contentUri, long durationMs){
            this.path = path;
            this.orientation = ori;
            this.gpsLocation = gpsStr;
            this.isVideo = isVideo;
            this.contentUri = contentUri;
            this.durationMs = durationMs;
        }
        public String path;
        public int orientation;
        public String gpsLocation;
        public boolean isVideo;
        public Uri contentUri;
        public long durationMs;
        public int width;
        public int height;
        public String mimeType;
        VideoPreparation.Prepared preparedVideo;
    }

    private static class MediaSlot {
        final ImageView imageView;
        final PlayerView playerView;
        final View videoInfo;
        final TextView videoTitle;
        final TextView videoDetails;
        final TextView imageLocation;

        MediaSlot(ImageView imageView, PlayerView playerView, View videoInfo,
                  TextView videoTitle, TextView videoDetails, TextView imageLocation) {
            this.imageView = imageView;
            this.playerView = playerView;
            this.videoInfo = videoInfo;
            this.videoTitle = videoTitle;
            this.videoDetails = videoDetails;
            this.imageLocation = imageLocation;
        }
    }

    private static class LayoutPlan {
        final int layoutRes;
        final int[] slotOrder;
        final List<PicItem> items;

        LayoutPlan(int layoutRes, int[] slotOrder, List<PicItem> items) {
            this.layoutRes = layoutRes;
            this.slotOrder = slotOrder;
            this.items = items;
        }
    }

    private class TwoPhotoPrefetch {
        final List<PicItem> items;
        final TwoLandscapeView.Frame frame;
        final String filesKey;

        TwoPhotoPrefetch(List<PicItem> items, TwoLandscapeView.Frame frame) {
            this.items = items;
            this.frame = frame;
            filesKey = pairKey(items);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storagePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (hasImagePermission()) {
                loadImageWithRetry();
            } else {
                Log.w("FirstFragment", "Required image permission denied; cannot load media.");
            }
            if (ENABLE_VIDEO && !hasVideoPermission()) {
                Log.w("FirstFragment", "Video permission denied; videos will be skipped.");
            }
        });
    }

    private boolean hasImagePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean hasVideoPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ENABLE_VIDEO) {
                storagePermissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO});
            } else {
                storagePermissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES});
            }
        } else {
            storagePermissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        dualPhotoAtmosphere = new DualPhotoAtmosphere();
        mediaAtmosphere = new MediaAtmosphereController();
        videoPreparation = new VideoPreparation();
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        gallerySettings = new GallerySettings(requireContext());
        gallerySettings.register(settingsListener);
        ((MainActivity) requireActivity()).getWebSettings().setMediaListener(() -> {
            handler.removeCallbacks(uploadRefreshRunnable);
            handler.postDelayed(uploadRefreshRunnable, 500);
        });
        // Load saved interval from preferences
        loadChangeInterval();
        loadDisplayMode();
        applyDisplayModeLayout();
        
        // Setup long press gesture detector for settings
        gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                showSettingsDialog();
            }
        });
        
        // Set touch listener on root view
        view.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        // 注册存储状态监听器
        registerStorageReceiver();

        scanExternalStorageMedia();
        if (hasImagePermission()) {
            loadImageWithRetry();
        } else {
            requestStoragePermission();
        }

//        String p1 = "/sdcard/DCIM/Camera/p1.jpg";
//        String p2 = "/sdcard/DCIM/Camera/p2.jpg";
//        Pair<Integer, Integer> dm = Utils.getScreenSize(getActivity());
//        Bitmap bitmap1 = ImageUtils.LoadBitmap(p1, dm.first / SCREEN_SHOW_COUNT, dm.second);
//        binding.imgview1.setImageBitmap(bitmap1);
//        Bitmap bitmap2 = ImageUtils.LoadBitmap(p2, dm.first / SCREEN_SHOW_COUNT, dm.second);
//        binding.imgview2.setImageBitmap(bitmap2);
//
//        Bitmap bmp = innerLoadImage(new PicItem(p1, 3), dm.first / SCREEN_SHOW_COUNT, dm.second);
//        binding.imgview1.setImageBitmap(bmp);
//        bmp = innerLoadImage(new PicItem(p2, 1), dm.first / SCREEN_SHOW_COUNT, dm.second);
//        binding.imgview2.setImageBitmap(bmp);


//        String time = getNowDay("HH:mm");
//        String date = getNowDay("MM月dd日 ") + getWeekOfDate(new Date());
//        binding.title.setText(time);
//        binding.subtitle.setText(date);
//
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                String time = getNowDay("HH:mm");
//                String date = getNowDay("MM月dd日 ") + getWeekOfDate(new Date());
//                binding.title.setText(time);
//                binding.subtitle.setText(date);
//            }
//        }, 1000 * 60);
    }

    public String getNowDay(String timeFormat){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(timeFormat);
        return simpleDateFormat.format(new Date()); //将给定的 Date 格式化为日期/时间字符串;
    }

    public String getWeekOfDate(Date dt) {
        String[] weekDays = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        Calendar cal = Calendar.getInstance();
        cal.setTime(dt);
        int w = cal.get(Calendar.DAY_OF_WEEK) - 1;
        if (w < 0)
            w = 0;
        return weekDays[w];
    }

    @Override
    public void onDestroyView() {
        landscapePreparing = false;
        mediaGeneration++;
        twoPhotoPrefetch = null;
        if (dualPhotoAtmosphere != null) dualPhotoAtmosphere.close();
        if (mediaAtmosphere != null) mediaAtmosphere.close();
        if (videoPreparation != null) videoPreparation.clear();
        ((MainActivity) requireActivity()).getWebSettings().setMediaListener(null);
        if (gallerySettings != null) gallerySettings.unregister(settingsListener);
        if (settingsDialog != null) settingsDialog.dismiss();
        handler.removeCallbacksAndMessages(null);
        hasMediaContent = false;
        releasePlayers();
        unregisterStorageReceiver();
        super.onDestroyView();
        binding = null;
    }

    private void registerStorageReceiver() {
        if (storageReceiver != null) {
            return;
        }
        storageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.i("FirstFragment", "Storage broadcast received: " + action);
                
                if (Intent.ACTION_MEDIA_MOUNTED.equals(action) || 
                    Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
                    // SD卡挂载完成或媒体扫描完成，重新加载图片
                    Log.i("FirstFragment", "Storage ready, reloading media...");
                    loadRetryCount = 0;
                    handler.postDelayed(() -> {
                        if (hasImagePermission()) {
                            loadImageWithRetry();
                        }
                    }, 500);
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        filter.addDataScheme("file");
        
        try {
            requireContext().registerReceiver(storageReceiver, filter);
            Log.i("FirstFragment", "Storage receiver registered");
        } catch (Exception e) {
            Log.e("FirstFragment", "Failed to register storage receiver", e);
        }
    }

    private void unregisterStorageReceiver() {
        if (storageReceiver != null) {
            try {
                requireContext().unregisterReceiver(storageReceiver);
                Log.i("FirstFragment", "Storage receiver unregistered");
            } catch (Exception e) {
                Log.e("FirstFragment", "Failed to unregister storage receiver", e);
            }
            storageReceiver = null;
        }
    }

    private void loadImageWithRetry() {
        if (!isAdded() || binding == null) {
            return;
        }
        
        // 检查存储状态
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            Log.w("FirstFragment", "External storage not mounted, state: " + state);
            if (loadRetryCount < MAX_RETRY_COUNT) {
                loadRetryCount++;
                Log.i("FirstFragment", "Retry loading media in " + RETRY_DELAY_MS + "ms (attempt " + loadRetryCount + "/" + MAX_RETRY_COUNT + ")");
                handler.postDelayed(this::loadImageWithRetry, RETRY_DELAY_MS);
            } else {
                Log.e("FirstFragment", "Max retry count reached, giving up");
                loadRetryCount = 0;
            }
            return;
        }
        
        loadRetryCount = 0;
        loadImage();
    }

    private int getRandNum(int endNum){
        if(endNum > 0){
            Random random = new Random();
            return random.nextInt(endNum);
        }
        return 0;
    }

    private Uri getMediaStoreUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            return MediaStore.Files.getContentUri("external");
        }
    }

    private void loadImage(){
        if (!isAdded()) {
            Log.w("FirstFragment", "Fragment not attached; skip loadImage.");
            return;
        }
        if (binding == null) {
            Log.w("FirstFragment", "Binding is null; view likely destroyed, skip loadImage.");
            return;
        }
        Context context = getContext();
        if (context == null) {
            Log.w("FirstFragment", "Context unavailable; skip loadImage.");
            return;
        }
        handler.removeCallbacks(refreshRunnable);
        refreshScheduled = false;
        nextRefreshDelayMs = CHANGE_TIMER;
        boolean explicitSingleMode = DISPLAY_MODE_SINGLE.equals(currentDisplayMode);
        int selectedCount = GallerySettings.selectCount(currentDisplayMode, random);
        boolean singleMode = selectedCount == 1;
        int desiredCount = explicitSingleMode ? SINGLE_MODE_CACHE_COUNT : selectedCount;
        int generation = ++mediaGeneration;
        landscapePreparing = false;
        if (landscapeTask != null) landscapeTask.cancel(true);
        if (videoPreparationTask != null) videoPreparationTask.cancel(true);
        if (twoPhotoPrefetchTask != null) twoPhotoPrefetchTask.cancel(true);
        TwoPhotoPrefetch ready = twoPhotoPrefetch;
        twoPhotoPrefetch = null;
        if (!canPrefetchTwoPhotos() || ready == null
                || !ready.filesKey.equals(pairKey(ready.items))) ready = null;
        ContentResolver cr = context.getContentResolver();
        boolean includeVideo = ENABLE_VIDEO && hasVideoPermission();
        String selection;
        if (includeVideo) {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        } else {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
        }
        if (Build.VERSION.SDK_INT >= 29) selection = "(" + selection + ") AND is_pending=0";
        Cursor cursor = null;
        try {
            cursor = cr.query(getMediaStoreUri(), new String[]{MediaStore.Files.FileColumns._ID}, selection, null, MediaStore.Files.FileColumns.DATE_ADDED + " DESC");
        } catch (IllegalArgumentException e) {
            Log.e("FirstFragment", "Failed to query media store", e);
        }
        if (cursor != null){
            int totalCount = cursor.getCount();
            cursor.close();

            int pageCount = (int) Math.ceil(totalCount / (double) PAGE_SIZE);
            if(pageCount <=0 ) {
                Log.w("FirstFragment", "No media found in MediaStore, totalCount: " + totalCount);
                hasMediaContent = false;
                // 如果没有找到媒体文件，可能是SD卡还在加载，尝试重试
                if (loadRetryCount < MAX_RETRY_COUNT) {
                    loadRetryCount++;
                    Log.i("FirstFragment", "No media found, retrying in " + RETRY_DELAY_MS + "ms (attempt " + loadRetryCount + "/" + MAX_RETRY_COUNT + ")");
                    handler.postDelayed(this::loadImageWithRetry, RETRY_DELAY_MS);
                } else {
                    Log.w("FirstFragment", "Max retry reached, no media available");
                    loadRetryCount = 0;
                    scheduleNextRefresh();
                }
                return;
            }

            List<PicItem> imagelist= new ArrayList<>();
            if (selectedCount == 2 && ready != null) imagelist.addAll(ready.items);
            int maxAttempts = pageCount > 0 ? pageCount * 2 : 10;
            int attempts = 0;
            while (imagelist.size() < desiredCount && attempts < maxAttempts) {
                randLoadImage(cr, pageCount, imagelist, includeVideo, desiredCount, singleMode);
                attempts++;
            }
            if (imagelist.isEmpty()) {
                Log.w("FirstFragment", "No images available to display.");
                hasMediaContent = false;
                if (singleMode) {
                    singleModeItems.clear();
                    clearSingleModeDisplay();
                }
                scheduleNextRefresh();
                return;
            }

            queueSampledItems(imagelist, selectedCount, ready == null ? null : ready.frame, generation);
            return;
        } else {
            hasMediaContent = false;
        }

        scheduleNextRefresh();
        if (canPrefetchTwoPhotos()) prefetchNextTwoPhotos(generation);
    }

    private void queueSampledItems(List<PicItem> items, int selectedCount,
                                   TwoLandscapeView.Frame ready, int generation) {
        if (selectedCount == 3) {
            prepareThreePhotoRound(items, generation);
            return;
        }
        boolean needsPreparation = false;
        for (PicItem item : items) {
            if (item.isVideo && item.preparedVideo == null) needsPreparation = true;
        }
        if (!needsPreparation) {
            displaySampledItems(items, selectedCount, ready, generation);
            return;
        }
        landscapePreparing = true;
        videoPreparationTask = dualPhotoAtmosphere.submit(() -> {
            try {
                prepareQueuedVideos(items);
                handler.post(() -> displaySampledItems(items, selectedCount, ready, generation));
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) return;
                Log.w("VideoPreparation", "Retaining current screen while video preparation failed", e);
                handler.post(() -> {
                    if (binding != null && generation == mediaGeneration) {
                        landscapePreparing = false;
                        scheduleNextRefresh();
                    }
                });
            }
        });
    }

    private ThreePhotoArrangement.Kind threePhotoKind(List<PicItem> items) {
        int[] widths = new int[items.size()], heights = new int[items.size()];
        boolean[] videos = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            widths[i] = items.get(i).width; heights[i] = items.get(i).height; videos[i] = items.get(i).isVideo;
        }
        return ThreePhotoArrangement.classify(widths, heights, videos);
    }

    private void prepareThreePhotoRound(List<PicItem> items, int generation) {
        landscapePreparing = true;
        ContentResolver resolver = requireContext().getContentResolver();
        boolean includeVideo = ENABLE_VIDEO && hasVideoPermission();
        landscapeTask = dualPhotoAtmosphere.submit(() -> {
            try {
                List<PicItem> selected = new ArrayList<>(items);
                prepareQueuedVideos(selected);
                for (PicItem item : selected) if (!item.isVideo) populateMediaMetadata(item);
                ThreePhotoArrangement.Kind kind = threePhotoKind(selected);
                if (kind == ThreePhotoArrangement.Kind.REDRAW) {
                    selected.clear();
                    String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + (includeVideo ? " IN (1,3)" : "=1");
                    if (Build.VERSION.SDK_INT >= 29) selection = "(" + selection + ") AND is_pending=0";
                    int count;
                    try (Cursor cursor = resolver.query(getMediaStoreUri(),
                            new String[]{MediaStore.Files.FileColumns._ID}, selection, null, null)) {
                        count = cursor == null ? 0 : cursor.getCount();
                    }
                    for (int attempt = 0; attempt < count * 2 && selected.size() < 3; attempt++) {
                        if (Thread.currentThread().isInterrupted()) return;
                        randLoadImage(resolver, count, selected, includeVideo, 3, false);
                    }
                    for (PicItem item : selected) if (!item.isVideo) populateMediaMetadata(item);
                    prepareQueuedVideos(selected);
                    kind = threePhotoKind(selected);
                }
                ThreePhotoArrangement.Kind resultKind = kind;
                handler.post(() -> {
                    if (binding == null || generation != mediaGeneration) return;
                    landscapePreparing = false;
                    if (resultKind == ThreePhotoArrangement.Kind.PORTRAIT_TWO_LANDSCAPES) {
                        showOnePortraitTwoLandscapes(selected, generation);
                        return;
                    }
                    if (getVideoCount(selected) == 1 && resultKind != ThreePhotoArrangement.Kind.REDRAW
                            && resultKind != ThreePhotoArrangement.Kind.UNSUPPORTED) {
                        showThreeMedia(selected, resultKind, generation);
                        return;
                    }
                    if (resultKind == ThreePhotoArrangement.Kind.PORTRAITS) showThreePortraitPhotos(selected, generation);
                    else if (resultKind == ThreePhotoArrangement.Kind.LANDSCAPES) {
                        showThreeStackPhotos(selected, resultKind, generation);
                    } else scheduleNextRefresh();
                });
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) return;
                Log.w("ThreePhoto", "Retaining previous screen", e);
                handler.post(() -> {
                    if (binding != null && generation == mediaGeneration) {
                        landscapePreparing = false; scheduleNextRefresh();
                    }
                });
            }
        });
    }

    /** All mode entries share the same ordering, geometry, photo background and video handling. */
    private void showOnePortraitTwoLandscapes(List<PicItem> items, int generation) {
        List<PicItem> ordered = new ArrayList<>(items);
        for (int i = 0; i < ordered.size(); i++) {
            if (isPortrait(ordered.get(i))) { Collections.swap(ordered, 0, i); break; }
        }
        if (getVideoCount(ordered) == 1) {
            showThreeMedia(ordered, ThreePhotoArrangement.Kind.PORTRAIT_TWO_LANDSCAPES, generation);
        } else {
            showThreeStackPhotos(ordered, ThreePhotoArrangement.Kind.PORTRAIT_TWO_LANDSCAPES, generation);
        }
    }

    private void showThreeStackPhotos(List<PicItem> items, ThreePhotoArrangement.Kind kind, int generation) {
        Pair<Integer, Integer> screen = getPlaybackCanvasSize();
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        landscapePreparing = true;
        landscapeTask = dualPhotoAtmosphere.submit(() -> {
            try {
                List<DualPhotoAtmosphere.Source> sources = new ArrayList<>();
                int leftWidth = ThreePhotoArrangement.leftWidth(screen.first, gap, kind);
                for (int i = 0; i < 3; i++) {
                    PicItem item = items.get(i);
                    int width = i == 0 ? leftWidth : screen.first - leftWidth - gap;
                    int height = i == 0 ? screen.second : screen.second / 2;
                    sources.add(new DualPhotoAtmosphere.Source(mediaKey(item), () -> decodeScaledBitmap(item, width, height)));
                }
                ThreeStackView.Frame frame = dualPhotoAtmosphere.prepareStack(sources, screen.first, screen.second, gap, kind);
                handler.post(() -> {
                    if (binding == null || generation != mediaGeneration) return;
                    landscapePreparing = false;
                    releasePlayers(); showingSingleMode = false; applyDisplayModeLayout();
                    if (!(currentMultiLayout instanceof ThreeStackView)) {
                        binding.multiMediaContainer.removeAllViews();
                        currentMultiLayout = new ThreeStackView(requireContext());
                        binding.multiMediaContainer.addView(currentMultiLayout, new ViewGroup.LayoutParams(-1, -1));
                        currentLayoutResId = 0; currentMediaSlots.clear();
                    }
                    ((ThreeStackView) currentMultiLayout).show(frame);
                    hasMediaContent = true; scheduleNextRefresh();
                });
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) return;
                Log.w("ThreePhoto", "Unable to prepare stack", e);
                handler.post(() -> {
                    if (binding != null && generation == mediaGeneration) {
                        landscapePreparing = false; scheduleNextRefresh();
                    }
                });
            }
        });
    }

    private Pair<Integer, Integer> getPlaybackCanvasSize() {
        // DisplayMetrics can exclude system-reserved pixels even when our window fills the display.
        if (binding != null) {
            View container = binding.multiMediaContainer;
            if (container.getWidth() > 0 && container.getHeight() > 0) {
                return new Pair<>(container.getWidth(), container.getHeight());
            }
            View root = binding.getRoot();
            if (root.getWidth() > 0 && root.getHeight() > 0) return new Pair<>(root.getWidth(), root.getHeight());
        }
        return Utils.getScreenSize(getActivity());
    }

    private void showThreeMedia(List<PicItem> items, ThreePhotoArrangement.Kind kind, int generation) {
        Pair<Integer, Integer> screen = getPlaybackCanvasSize();
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        landscapePreparing = true;
        landscapeTask = dualPhotoAtmosphere.submit(() -> {
            try {
                Bitmap[] photos = new Bitmap[3];
                for (int i = 0; i < 3; i++) {
                    PicItem item = items.get(i);
                    if (!item.isVideo) {
                        photos[i] = decodeScaledBitmap(item, screen.first / 2, screen.second);
                        if (photos[i] == null) throw new IllegalStateException("无法解码三图照片");
                    }
                }
                handler.post(() -> {
                    if (binding == null || generation != mediaGeneration) return;
                    releasePlayers();
                    int[] widths = new int[3], heights = new int[3];
                    for (int i = 0; i < 3; i++) { widths[i] = items.get(i).width; heights[i] = items.get(i).height; }
                    ThreeMediaLayout root = new ThreeMediaLayout(requireContext(), widths, heights, kind, gap);
                    View template = getLayoutInflater().inflate(R.layout.layout_media_three_portrait, root, false);
                    EqualHeightPhotoLayout.Slot[] columns = kind == ThreePhotoArrangement.Kind.PORTRAITS
                            ? EqualHeightPhotoLayout.calculate(widths, heights, screen.first, screen.second, gap) : null;
                    int leftWidth = ThreePhotoArrangement.leftWidth(screen.first, gap, kind);
                    int topHeight = (screen.second - gap) / 2;
                    binding.multiMediaContainer.removeAllViews();
                    currentMultiLayout = root; currentLayoutResId = 0; currentMediaSlots.clear();
                    int[] frames = {R.id.frame1, R.id.frame2, R.id.frame3};
                    for (int i = 0; i < 3; i++) {
                        View frame = template.findViewById(frames[i]);
                        ((ViewGroup) frame.getParent()).removeView(frame);
                        int x, y, width, height;
                        if (columns != null) {
                            x = columns[i].left; y = 0; width = columns[i].right - x; height = screen.second;
                        } else {
                            x = i == 0 ? 0 : leftWidth + gap;
                            y = i < 2 ? 0 : topHeight + gap;
                            width = i == 0 ? leftWidth : screen.first - leftWidth - gap;
                            height = i == 0 ? screen.second : i == 1 ? topHeight : screen.second - y;
                        }
                        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(width, height);
                        params.leftMargin = x; params.topMargin = y;
                        root.addView(frame, params);
                        ImageView image = frame.findViewById(IMAGE_VIEW_IDS[i]);
                        boolean crop = columns != null && columns[i].sourceTop > 0.01;
                        image.setScaleType(crop ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
                    }
                    bindMediaSlots(new LayoutPlan(0, new int[]{0, 1, 2}, items));
                    showingSingleMode = false; applyDisplayModeLayout();
                    binding.multiMediaContainer.addView(root, new ViewGroup.LayoutParams(-1, -1));
                    for (int i = 0; i < 3; i++) {
                        PicItem item = items.get(i); MediaSlot slot = currentMediaSlots.get(i);
                        if (item.isVideo) showVideo(item, slot);
                        else {
                            slot.imageView.setImageBitmap(photos[i]); slot.imageView.setVisibility(View.VISIBLE);
                            updateMediaBackground(slot.imageView, photos[i], mediaKey(item));
                            showImageLocation(item, slot.imageLocation);
                        }
                    }
                    landscapePreparing = false; hasMediaContent = true; scheduleNextRefresh();
                });
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) return;
                Log.w("ThreeMedia", "Retaining previous screen", e);
                handler.post(() -> {
                    if (binding != null && generation == mediaGeneration) {
                        landscapePreparing = false; scheduleNextRefresh();
                    }
                });
            }
        });
    }

    private void prepareQueuedVideos(List<PicItem> items) throws Exception {
        for (PicItem item : items) {
            if (!item.isVideo || item.preparedVideo != null) continue;
            VideoPreparation.Prepared prepared = videoPreparation.prepare(item.path);
            item.preparedVideo = prepared;
            item.height = (prepared.rotation == 90 || prepared.rotation == 270) ? prepared.width : prepared.height;
            item.height = Math.max(1, item.height);
            item.width = Math.max(1, (int) Math.round(item.height * prepared.displayAspect));
            item.durationMs = prepared.durationMs;
            item.mimeType = prepared.mime;
        }
    }

    private void displaySampledItems(List<PicItem> imagelist, int selectedCount,
                                     TwoLandscapeView.Frame ready, int generation) {
        if (binding == null || generation != mediaGeneration) return;
        landscapePreparing = false;
        LayoutPlan fourImagePlan = null;
        if (selectedCount == 4) {
            LayoutPlan candidate = fourImagePlan = createLayoutPlan(imagelist);
            if (candidate != null && candidate.layoutRes == R.layout.layout_media_portrait_left) {
                showOnePortraitTwoLandscapes(candidate.items, generation);
                return;
            }
            if (candidate != null && candidate.layoutRes == R.layout.layout_media_three_portrait
                    && isThreePortraitPhotos(candidate.items)) {
                showThreePortraitPhotos(candidate.items, generation);
                return;
            }
        }
        releasePlayers();
        boolean singleMode = selectedCount == 1;
        Pair<Integer, Integer> dm = Utils.getScreenSize(getActivity());
        if (singleMode) {
            showingSingleMode = true;
            applyDisplayModeLayout();
            singleModeItems.clear();
            singleModeItems.addAll(imagelist);
            hasMediaContent = !singleModeItems.isEmpty();
            if (hasMediaContent) {
                singleModeIndex = Math.min(singleModeIndex, Math.max(0, singleModeItems.size() - 1));
                if (singleModeIndex >= singleModeItems.size()) {
                    singleModeIndex = 0;
                }
                displaySingleModeItem(dm.first, dm.second);
            } else {
                clearSingleModeDisplay();
            }
        } else {
            LayoutPlan layoutPlan = selectedCount == 2
                    ? createTwoImageLayoutPlan(imagelist) : fourImagePlan;
            if (layoutPlan == null || layoutPlan.items.isEmpty()) {
                hasMediaContent = false;
                scheduleNextRefresh();
                return;
            }
            if (selectedCount == 2 && isTwoLandscapePhotos(layoutPlan.items)) {
                showTwoLandscapePhotos(layoutPlan.items, dm.first, dm.second,
                        ready, generation);
                return;
            }
            showingSingleMode = false;
            applyDisplayModeLayout();
            View layout = ensureMultiLayout(layoutPlan.layoutRes);
            if (layout == null) {
                hasMediaContent = false;
                return;
            }
            if (selectedCount == 2) {
                configureTwoImageLayout(layout, layoutPlan.items, dm.first, dm.second);
            }
            bindMediaSlots(layoutPlan);
            int slotWidth = dm.first / GRID_COLUMNS;
            int slotHeight = selectedCount == 2 ? dm.second : dm.second / GRID_ROWS;
            for (int i = 0; i < currentMediaSlots.size(); i++) {
                MediaSlot slot = currentMediaSlots.get(i);
                if (i < layoutPlan.items.size()) {
                    PicItem item = layoutPlan.items.get(i);
                    if (item.isVideo) {
                        showVideo(item, slot);
                    } else {
                        showImage(item, slot.imageView, slot.playerView, slot.videoInfo, slot.imageLocation, slotWidth, slotHeight);
                    }
                } else {
                    clearMediaSlot(slot);
                }
            }
            hasMediaContent = true;
        }
        scheduleNextRefresh();
        if (canPrefetchTwoPhotos()) prefetchNextTwoPhotos(generation);
    }

    private boolean isTwoLandscapePhotos(List<PicItem> items) {
        return items.size() == 2 && !items.get(0).isVideo && !items.get(1).isVideo
                && items.get(0).width > items.get(0).height && items.get(0).height > 0
                && items.get(1).width > items.get(1).height && items.get(1).height > 0;
    }

    private boolean isThreePortraitPhotos(List<PicItem> items) {
        int[] widths = new int[items.size()], heights = new int[items.size()];
        boolean[] videos = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            widths[i] = items.get(i).width; heights[i] = items.get(i).height; videos[i] = items.get(i).isVideo;
        }
        return ThreePortraitPolicy.supports(widths, heights, videos);
    }

    private void showThreePortraitPhotos(List<PicItem> items, int generation) {
        Pair<Integer, Integer> screen = getPlaybackCanvasSize();
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        landscapePreparing = true;
        landscapeTask = dualPhotoAtmosphere.submit(() -> {
            try {
                java.util.List<DualPhotoAtmosphere.Source> sources = new ArrayList<>();
                int[] widths = new int[3], heights = new int[3];
                for (int i = 0; i < 3; i++) {
                    widths[i] = items.get(i).width; heights[i] = items.get(i).height;
                }
                EqualHeightPhotoLayout.Slot[] targets = EqualHeightPhotoLayout.calculate(widths, heights,
                        screen.first, screen.second, gap);
                for (int i = 0; i < items.size(); i++) {
                    PicItem item = items.get(i);
                    int decodeWidth = targets[i].right - targets[i].left;
                    sources.add(new DualPhotoAtmosphere.Source(mediaKey(item),
                            () -> decodeScaledBitmap(item, decodeWidth, screen.second)));
                }
                ThreePortraitView.Frame frame = dualPhotoAtmosphere.prepareThree(sources, screen.first, screen.second, gap);
                handler.post(() -> {
                    if (binding == null || generation != mediaGeneration) return;
                    landscapePreparing = false;
                    releasePlayers();
                    showingSingleMode = false;
                    applyDisplayModeLayout();
                    if (!(currentMultiLayout instanceof ThreePortraitView)) {
                        binding.multiMediaContainer.removeAllViews();
                        currentMultiLayout = new ThreePortraitView(requireContext());
                        binding.multiMediaContainer.addView(currentMultiLayout, new ViewGroup.LayoutParams(-1, -1));
                        currentLayoutResId = 0;
                        currentMediaSlots.clear();
                    }
                    ((ThreePortraitView) currentMultiLayout).show(frame);
                    hasMediaContent = true;
                    scheduleNextRefresh();
                    if (canPrefetchTwoPhotos()) prefetchNextTwoPhotos(generation);
                });
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) return;
                Log.w("ThreePortrait", "Retaining previous screen", e);
                handler.post(() -> {
                    if (binding != null && generation == mediaGeneration) {
                        landscapePreparing = false;
                        scheduleNextRefresh();
                    }
                });
            }
        });
    }

    private String mediaKey(PicItem item) {
        java.io.File file = new java.io.File(item.path);
        return item.contentUri + "|" + item.path + "|" + file.length() + "|"
                + file.lastModified() + "|" + item.orientation;
    }

    private String pairKey(List<PicItem> items) {
        StringBuilder key = new StringBuilder();
        for (PicItem item : items) key.append(mediaKey(item)).append('\n');
        return key.toString();
    }

    private TwoLandscapeView.Frame prepareTwoLandscapePhotos(List<PicItem> items, int width, int height)
            throws Exception {
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        PicItem left = items.get(0), right = items.get(1);
        int[] widths = TwoImageSizing.widths(width - gap, height,
                left.width, left.height, right.width, right.height);
        int leftHeight = Math.max(1, Math.min(height, Math.round(widths[0] * left.height / (float) left.width)));
        int rightHeight = Math.max(1, Math.min(height, Math.round(widths[1] * right.height / (float) right.width)));
        return dualPhotoAtmosphere.prepare(
                new DualPhotoAtmosphere.Source(mediaKey(left), () -> decodeScaledBitmap(left, widths[0], leftHeight)),
                new DualPhotoAtmosphere.Source(mediaKey(right), () -> decodeScaledBitmap(right, widths[1], rightHeight)),
                width, height, gap);
    }

    private void showTwoLandscapePhotos(List<PicItem> items, int width, int height,
                                       TwoLandscapeView.Frame ready, int generation) {
        landscapePreparing = true;
        if (ready != null && ready.width == width && ready.height == height) {
            displayTwoLandscapeFrame(ready, generation);
            return;
        }
        landscapeTask = dualPhotoAtmosphere.submit(() -> {
            try {
                TwoLandscapeView.Frame frame = prepareTwoLandscapePhotos(items, width, height);
                handler.post(() -> displayTwoLandscapeFrame(frame, generation));
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) return;
                Log.w("DualAtmosphere", "Unable to prepare photos; retaining current screen", e);
                handler.post(() -> {
                    if (binding != null && generation == mediaGeneration) {
                        landscapePreparing = false;
                        scheduleNextRefresh();
                    }
                });
            }
        });
    }

    private void displayTwoLandscapeFrame(TwoLandscapeView.Frame frame, int generation) {
        if (binding == null || generation != mediaGeneration) return;
        landscapePreparing = false;
        showingSingleMode = false;
        applyDisplayModeLayout();
        if (!(currentMultiLayout instanceof TwoLandscapeView)) {
            binding.multiMediaContainer.removeAllViews();
            currentMultiLayout = new TwoLandscapeView(requireContext());
            binding.multiMediaContainer.addView(currentMultiLayout,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            currentLayoutResId = 0;
            currentMediaSlots.clear();
        }
        ((TwoLandscapeView) currentMultiLayout).show(frame);
        hasMediaContent = true;
        scheduleNextRefresh();
        prefetchNextTwoPhotos(generation);
    }

    private void prefetchNextTwoPhotos(int generation) {
        if (!canPrefetchTwoPhotos() || binding == null) return;
        ContentResolver resolver = requireContext().getContentResolver();
        boolean includeVideo = ENABLE_VIDEO && hasVideoPermission();
        Pair<Integer, Integer> screen = Utils.getScreenSize(getActivity());
        twoPhotoPrefetchTask = dualPhotoAtmosphere.submit(() -> {
            try {
                String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + (includeVideo ? " IN (1,3)" : "=1");
                if (Build.VERSION.SDK_INT >= 29) selection = "(" + selection + ") AND is_pending=0";
                int count;
                try (Cursor cursor = resolver.query(getMediaStoreUri(),
                        new String[]{MediaStore.Files.FileColumns._ID}, selection, null, null)) {
                    count = cursor == null ? 0 : cursor.getCount();
                }
                List<PicItem> selected = new ArrayList<>();
                for (int attempt = 0; attempt < count * 2 && selected.size() < 2; attempt++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    randLoadImage(resolver, count, selected, includeVideo, 2, false);
                }
                if (selected.isEmpty()) return;
                prepareQueuedVideos(selected);
                LayoutPlan plan = createTwoImageLayoutPlan(selected);
                TwoLandscapeView.Frame frame = isTwoLandscapePhotos(plan.items)
                        ? prepareTwoLandscapePhotos(plan.items, screen.first, screen.second) : null;
                TwoPhotoPrefetch prepared = new TwoPhotoPrefetch(plan.items, frame);
                handler.post(() -> {
                    if (binding != null && generation == mediaGeneration
                            && canPrefetchTwoPhotos()) twoPhotoPrefetch = prepared;
                });
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) Log.w("DualAtmosphere", "Photo prefetch skipped", e);
            }
        });
    }

    private boolean canPrefetchTwoPhotos() {
        return GallerySettings.TWO.equals(currentDisplayMode) || GallerySettings.RANDOM.equals(currentDisplayMode);
    }

    private void showImage(PicItem item, ImageView imageView, PlayerView playerView, View videoInfo, TextView locationView, int imgWidth, int imgHeight) {
        if (playerView != null) {
            playerView.setPlayer(null);
            playerView.setVisibility(View.GONE);
        }
        if (videoInfo != null) {
            videoInfo.setVisibility(View.GONE);
        }
        imageView.setVisibility(View.VISIBLE);
        if (currentLayoutResId == R.layout.layout_media_two_portrait
                && (imageView.getId() == R.id.imgview3 || imageView.getId() == R.id.imgview4)) {
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        Bitmap bmp = decodeScaledBitmap(item, imgWidth, imgHeight);
        updateMediaBackground(imageView, bmp, mediaKey(item));
        if (bmp != null) {
            imageView.setImageBitmap(bmp);
        } else {
            imageView.setImageDrawable(null);
        }
        // 根据图片宽高比调整容器宽度
        adjustFrameLayoutRatio(imageView, item);
        // 显示位置信息
        if (locationView != null) {
            if (isNetworkAvailable()) {
                showImageLocation(item, locationView);
            } else {
                locationView.setVisibility(View.GONE);
                Log.w("FirstFragment", "Network unavailable; skip image geocoding.");
            }
        }
    }

    private void updateMediaBackground(View foreground, Bitmap bitmap, String key) {
        if (mediaAtmosphere == null) return;
        if (bitmap == null) mediaAtmosphere.clear(foreground);
        else mediaAtmosphere.showPhoto(foreground, bitmap, key);
    }

    private void adjustFrameLayoutRatio(ImageView imageView, PicItem item) {
        if (imageView == null || item == null || item.width <= 0 || item.height <= 0) {
            return;
        }
        ViewGroup parent = (ViewGroup) imageView.getParent();
        if (parent == null) {
            return;
        }
        if (parent instanceof androidx.constraintlayout.widget.ConstraintLayout) {
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = 
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) parent.getLayoutParams();
            if (lp != null) {
                lp.dimensionRatio = item.width + ":" + item.height;
                lp.matchConstraintDefaultWidth = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD;
                parent.setLayoutParams(lp);
                parent.requestLayout();
            }
        }
    }

    private void clearMediaSlot(MediaSlot slot) {
        if (slot == null) {
            return;
        }
        updateMediaBackground(slot.imageView, null, "");
        if (slot.imageView != null) {
            slot.imageView.setImageDrawable(null);
            slot.imageView.setVisibility(View.GONE);
        }
        if (slot.playerView != null) {
            slot.playerView.setPlayer(null);
            slot.playerView.setVisibility(View.GONE);
        }
        if (slot.videoInfo != null) {
            slot.videoInfo.setVisibility(View.GONE);
        }
        if (slot.videoTitle != null) {
            slot.videoTitle.setText("");
        }
        if (slot.videoDetails != null) {
            slot.videoDetails.setText("");
        }
        if (slot.imageLocation != null) {
            slot.imageLocation.setVisibility(View.GONE);
        }
    }

    private void clearSingleModeDisplay() {
        if (binding == null) {
            return;
        }
        clearSingleImageBackground();
        binding.singleImageView.setImageDrawable(null);
        binding.singleImageView.setVisibility(View.GONE);
        binding.singleVideoView.setPlayer(null);
        binding.singleVideoView.setVisibility(View.GONE);
        binding.singleVideoInfo.setVisibility(View.GONE);
        binding.singleImageLocation.setVisibility(View.GONE);
    }

    private void applyDisplayModeLayout() {
        if (binding == null) {
            return;
        }
        boolean singleMode = showingSingleMode;
        binding.singleImageContainer.setVisibility(singleMode ? View.VISIBLE : View.GONE);
        if (binding.multiMediaContainer != null) {
            binding.multiMediaContainer.setVisibility(singleMode ? View.GONE : View.VISIBLE);
        }
    }

    private void displaySingleModeItem(int screenWidth, int screenHeight) {
        if (binding == null || singleModeItems.isEmpty() || singleModeIndex < 0 || singleModeIndex >= singleModeItems.size()) {
            clearSingleModeDisplay();
            return;
        }
        PicItem item = singleModeItems.get(singleModeIndex);
        if (item.isVideo) {
            binding.singleImageView.setVisibility(View.GONE);
            showVideoInSingleMode(item);
        } else {
            showImageInSingleMode(item, screenWidth, screenHeight);
        }
    }

    private View ensureMultiLayout(@LayoutRes int layoutRes) {
        if (binding == null || binding.multiMediaContainer == null) {
            return null;
        }
        if (currentMultiLayout != null && currentLayoutResId == layoutRes) {
            return currentMultiLayout;
        }
        binding.multiMediaContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(binding.getRoot().getContext());
        currentMultiLayout = inflater.inflate(layoutRes, binding.multiMediaContainer, false);
        binding.multiMediaContainer.addView(currentMultiLayout);
        currentLayoutResId = layoutRes;
        currentMediaSlots.clear();
        return currentMultiLayout;
    }

    private void bindMediaSlots(LayoutPlan layoutPlan) {
        if (currentMultiLayout == null) {
            currentMediaSlots.clear();
            return;
        }
        currentMediaSlots.clear();
        int[] slotOrder = layoutPlan.slotOrder != null ? layoutPlan.slotOrder : DEFAULT_SLOT_ORDER;
        boolean[] used = new boolean[IMAGE_VIEW_IDS.length];
        for (int index : slotOrder) {
            appendSlot(index, used);
        }
        if (currentMediaSlots.isEmpty()) {
            for (int index : DEFAULT_SLOT_ORDER) {
                appendSlot(index, used);
            }
        }
    }

    private void appendSlot(int index, boolean[] used) {
        if (index < 0 || index >= IMAGE_VIEW_IDS.length || used[index]) {
            return;
        }
        if (currentMultiLayout == null) {
            return;
        }
        ImageView imageView = currentMultiLayout.findViewById(IMAGE_VIEW_IDS[index]);
        if (imageView == null) {
            return;
        }
        PlayerView playerView = currentMultiLayout.findViewById(VIDEO_VIEW_IDS[index]);
        View videoInfo = currentMultiLayout.findViewById(VIDEO_INFO_IDS[index]);
        TextView videoTitle = currentMultiLayout.findViewById(VIDEO_TITLE_IDS[index]);
        TextView videoDetails = currentMultiLayout.findViewById(VIDEO_DETAILS_IDS[index]);
        TextView imageLocation = currentMultiLayout.findViewById(IMAGE_LOCATION_IDS[index]);
        currentMediaSlots.add(new MediaSlot(imageView, playerView, videoInfo, videoTitle, videoDetails, imageLocation));
        used[index] = true;
    }

    private LayoutPlan createTwoImageLayoutPlan(List<PicItem> items) {
        List<PicItem> selected = new ArrayList<>(items);
        for (PicItem item : selected) {
            populateMediaMetadata(item);
        }
        // 混合方向时固定竖图在左，横图在右。
        if (selected.size() == 2 && !isPortrait(selected.get(0)) && isPortrait(selected.get(1))) {
            Collections.swap(selected, 0, 1);
        }
        return new LayoutPlan(R.layout.layout_media_two, new int[]{0, 1}, selected);
    }

    private void configureTwoImageLayout(View layout, List<PicItem> items, int screenWidth, int screenHeight) {
        View left = layout.findViewById(R.id.frame_portrait_left);
        View right = layout.findViewById(R.id.frame_portrait_right);
        android.widget.LinearLayout.LayoutParams leftParams =
                (android.widget.LinearLayout.LayoutParams) left.getLayoutParams();
        android.widget.LinearLayout.LayoutParams rightParams =
                (android.widget.LinearLayout.LayoutParams) right.getLayoutParams();
        leftParams.width = 0;
        rightParams.width = 0;
        leftParams.weight = 1;
        rightParams.weight = 1;
        ImageView leftImage = layout.findViewById(R.id.imgview1);
        leftImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (items.size() == 2) {
            PicItem first = items.get(0);
            PicItem second = items.get(1);
            int availableWidth = screenWidth - leftParams.getMarginEnd()
                    - rightParams.getMarginStart();
            int[] widths = TwoImageSizing.widths(availableWidth, screenHeight,
                    first.width, first.height, second.width, second.height);
            leftParams.width = widths[0];
            rightParams.width = widths[1];
            leftParams.weight = 0;
            rightParams.weight = 0;
            if (isPortrait(first) && !isPortrait(second)) {
                // 极端宽高比时允许横向裁切，保证竖图始终填满高度。
                leftImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        }
        left.setLayoutParams(leftParams);
        right.setLayoutParams(rightParams);
    }

    private LayoutPlan createLayoutPlan(List<PicItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        List<PicItem> portraits = new ArrayList<>();
        List<PicItem> landscapes = new ArrayList<>();
        List<PicItem> shuffledAll = new ArrayList<>();
        for (PicItem item : items) {
            if (item == null) {
                continue;
            }
            populateMediaMetadata(item);
            if (isPortrait(item)) {
                portraits.add(item);
            } else {
                landscapes.add(item);
            }
            shuffledAll.add(item);
        }
        if (shuffledAll.isEmpty()) {
            return null;
        }
        Collections.shuffle(portraits, random);
        Collections.shuffle(landscapes, random);
        Collections.shuffle(shuffledAll, random);

        if (portraits.size() >= 3) {
            List<PicItem> selected = new ArrayList<>(portraits.subList(0, 3));
            return new LayoutPlan(R.layout.layout_media_three_portrait, new int[]{0, 1, 2}, selected);
        }

        if (portraits.size() == 2 && landscapes.size() >= 2) {
            List<PicItem> selected = new ArrayList<>();
            selected.addAll(portraits.subList(0, 2));
            selected.addAll(landscapes.subList(0, 2));
            return new LayoutPlan(R.layout.layout_media_two_portrait, DEFAULT_SLOT_ORDER, selected);
        }

        if (portraits.size() >= 1 && landscapes.size() >= 2) {
            List<PicItem> selected = new ArrayList<>();
            selected.add(portraits.get(0));
            selected.addAll(landscapes.subList(0, 2));
            return new LayoutPlan(R.layout.layout_media_portrait_left, new int[]{0, 1, 2}, selected);
        }

        // Fallback：田字格布局，最多展示4张
        List<PicItem> fallback = new ArrayList<>();
        int limit = Math.min(shuffledAll.size(), GRID_SLOT_COUNT);
        for (int i = 0; i < limit; i++) {
            fallback.add(shuffledAll.get(i));
        }
        return new LayoutPlan(R.layout.layout_media_grid, DEFAULT_SLOT_ORDER, fallback);
    }

    private void populateMediaMetadata(PicItem item) {
        if (item == null) {
            return;
        }
        if (item.isVideo) {
            if (item.width > 0 && item.height > 0 && item.mimeType != null) {
                return;
            }
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(item.path);
                String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                if (!TextUtils.isEmpty(widthStr)) {
                    item.width = Integer.parseInt(widthStr);
                }
                if (!TextUtils.isEmpty(heightStr)) {
                    item.height = Integer.parseInt(heightStr);
                }
                if (item.durationMs <= 0) {
                    String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (!TextUtils.isEmpty(durationStr)) {
                        item.durationMs = Long.parseLong(durationStr);
                    }
                }
                if (item.mimeType == null) {
                    item.mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                }
            } catch (Exception e) {
                Log.w("FirstFragment", "Failed to load video metadata", e);
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }
        } else {
            if (item.width > 0 && item.height > 0 && item.mimeType != null) {
                return;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(item.path, options);
            if (options.outWidth > 0 && options.outHeight > 0) {
                if(item.orientation<=4){
                    item.width = options.outWidth;
                    item.height = options.outHeight;
                }else{
                    item.width = options.outHeight;
                    item.height = options.outWidth;
                }
            }
            if (item.mimeType == null) {
                item.mimeType = options.outMimeType;
            }
        }
    }

    private boolean isPortrait(PicItem item) {
        if (item == null) {
            return false;
        }
        if (item.width > 0 && item.height > 0) {
            return item.height >= item.width;
        }
        // 当缺失尺寸信息时，保守地视为横图以回退到田字格
        return false;
    }

    private void showImageInSingleMode(PicItem item, int screenWidth, int screenHeight) {
        binding.singleVideoView.setPlayer(null);
        binding.singleVideoView.setVisibility(View.GONE);
        binding.singleVideoInfo.setVisibility(View.GONE);
        binding.singleImageView.setVisibility(View.VISIBLE);
        clearSingleImageBackground();
        Bitmap bmp = decodeScaledBitmap(item, screenWidth, screenHeight);
        boolean landscape = bmp != null && bmp.getWidth() > bmp.getHeight();
        if (bmp != null) {
            binding.singleImageView.setScaleType(landscape
                    ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            binding.singleImageView.setImageBitmap(bmp);
        } else {
            binding.singleImageView.setImageDrawable(null);
        }
        updateMediaBackground(binding.singleImageView, landscape ? null : bmp, mediaKey(item));
        updateSingleLocation(item);
    }

    private void clearSingleImageBackground() {
        if (mediaAtmosphere != null) mediaAtmosphere.clear(binding.singleImageView);
        binding.singleImageBackground.setImageDrawable(null);
        binding.singleImageBackground.setVisibility(View.GONE);
    }

    private void showVideoInSingleMode(PicItem item) {
        clearSingleImageBackground();
        binding.singleImageView.setImageDrawable(null);
        binding.singleImageView.setVisibility(View.GONE);
        binding.singleVideoView.setVisibility(View.VISIBLE);
        binding.singleVideoInfo.setVisibility(View.GONE);

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(requireContext())
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setTargetBufferBytes(8 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .setBufferDurationsMs(
                        VIDEO_BACKBUFFER_MS,
                        VIDEO_BUFFER_MS,
                        VIDEO_BACKBUFFER_MS,
                        VIDEO_BACKBUFFER_MS)
                .build();

        ExoPlayer player = new ExoPlayer.Builder(requireContext(), renderersFactory)
                .setLoadControl(loadControl)
                .build();

        TrackSelectionParameters.Builder trackBuilder = player.getTrackSelectionParameters().buildUpon()
                .setPreferredVideoMimeTypes(
                        MimeTypes.VIDEO_H265,
                        MimeTypes.VIDEO_H264,
                        MimeTypes.VIDEO_VP9,
                        MimeTypes.VIDEO_AV1);
        player.setTrackSelectionParameters(trackBuilder.build());
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setMediaItem(MediaItem.fromUri(item.contentUri));
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    long duration = player.getDuration();
                    if (duration != C.TIME_UNSET) {
                        extendRefreshIfNeeded(duration);
                    }
                }
            }
        });
        player.prepare();
        long immediateDuration = player.getDuration();
        if (immediateDuration != C.TIME_UNSET) {
            extendRefreshIfNeeded(immediateDuration);
        }
        player.play();
        binding.singleVideoView.setPlayer(player);
        activePlayers.add(player);
        mediaAtmosphere.showVideo(binding.singleVideoView, player, item.preparedVideo);
        updateSingleLocation(item);
    }

    private void updateSingleLocation(PicItem item) {
        if (TextUtils.isEmpty(item.gpsLocation)) {
            binding.singleImageLocation.setVisibility(View.GONE);
            return;
        }
        binding.singleImageLocation.setVisibility(View.VISIBLE);
        if (isNetworkAvailable()) {
            getLocation(item.gpsLocation, binding.singleImageLocation);
        }
    }

    private void showVideo(PicItem item, MediaSlot slot) {
        if (slot == null || slot.playerView == null) {
            Log.w("FirstFragment", "Missing player view for video slot");
            return;
        }
        updateMediaBackground(slot.imageView, null, "");
        if (slot.imageView != null) {
            slot.imageView.setImageDrawable(null);
            slot.imageView.setVisibility(View.GONE);
        }
        slot.playerView.setVisibility(View.VISIBLE);
        if (slot.videoInfo != null) {
            slot.videoInfo.setVisibility(View.GONE);
        }

        // API 29+ HDR 支持：设置窗口为 HDR 模式（仅当设备支持 HDR 时）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isHdrSupported()) {
            requireActivity().getWindow().addFlags(LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            requireActivity().getWindow().setColorMode(ActivityInfo.COLOR_MODE_HDR);
        }

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(requireContext())
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setTargetBufferBytes(8 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .setBufferDurationsMs(
                        VIDEO_BACKBUFFER_MS,
                        VIDEO_BUFFER_MS,
                        VIDEO_BACKBUFFER_MS,
                        VIDEO_BACKBUFFER_MS)
                .build();

        ExoPlayer player = new ExoPlayer.Builder(requireContext(), renderersFactory)
                .setLoadControl(loadControl)
                .build();
        
        TrackSelectionParameters.Builder trackBuilder = player.getTrackSelectionParameters().buildUpon()
                .setPreferredVideoMimeTypes(
                        MimeTypes.VIDEO_H265,
                        MimeTypes.VIDEO_H264,
                        MimeTypes.VIDEO_VP9,
                        MimeTypes.VIDEO_AV1);
        player.setTrackSelectionParameters(trackBuilder.build());
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setMediaItem(MediaItem.fromUri(item.contentUri));
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    long duration = player.getDuration();
                    if (duration != C.TIME_UNSET) {
                        extendRefreshIfNeeded(duration);
                    }
                }
            }
        });
        player.prepare();
        long immediateDuration = player.getDuration();
        if (immediateDuration != C.TIME_UNSET) {
            extendRefreshIfNeeded(immediateDuration);
        }
        player.play();
        slot.playerView.setPlayer(player);
        activePlayers.add(player);
        mediaAtmosphere.showVideo(slot.playerView, player, item.preparedVideo);

        // 显示位置信息
//        if (isNetworkAvailable()) {
//            showVideoLocation(item, playerView);
//        } else {
//            Log.w("FirstFragment", "Network unavailable; skip video geocoding.");
//        }
    }

    private String getFileSize(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);
            long bytes = file.length();
            return formatFileSize(bytes);
        } catch (Exception e) {
            Log.w("FirstFragment", "Failed to get file size", e);
            return "Unknown";
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0) return "00:00";
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        } else {
            return String.format("%02d:%02d", minutes, seconds % 60);
        }
    }

    private void showImageLocation(PicItem item, TextView locationView) {
        if (locationView == null) {
            return;
        }
        if (TextUtils.isEmpty(item.gpsLocation)) {
            locationView.setVisibility(View.GONE);
            return;
        }
        locationView.setVisibility(View.VISIBLE);
        getLocation(item.gpsLocation, locationView);
    }

    private boolean isHdrSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26+
            try {
                DisplayManager dm = (DisplayManager) requireContext().getSystemService(Context.DISPLAY_SERVICE);
                android.view.Display display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY);
                return display.isHdr();
            } catch (Exception e) {
                Log.w("FirstFragment", "Failed to check HDR support", e);
            }
        }
        return false;
    }

    private void releasePlayers() {
        if (mediaAtmosphere != null) mediaAtmosphere.stopVideos();
        // API 29+ HDR 支持：恢复窗口为正常模式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requireActivity().getWindow().setColorMode(ActivityInfo.COLOR_MODE_DEFAULT);
        }

        for (ExoPlayer player : activePlayers) {
            try {
                player.stop();
                player.release();
            } catch (Exception e) {
                Log.e("FirstFragment", "Error releasing player", e);
            }
        }
        activePlayers.clear();
        if (binding != null) {
            binding.singleVideoView.setPlayer(null);
        }
        for (MediaSlot slot : currentMediaSlots) {
            if (slot != null && slot.playerView != null) {
                slot.playerView.setPlayer(null);
            }
        }
        System.gc();
    }

    private void scheduleNextRefresh() {
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, Math.max(1, nextRefreshDelayMs));
        refreshScheduled = true;
    }

    private void extendRefreshIfNeeded(long durationMs) {
        if (durationMs <= 0) {
            return;
        }
        long candidateDelay = Math.max(durationMs, CHANGE_TIMER);
        if (candidateDelay > nextRefreshDelayMs) {
            nextRefreshDelayMs = candidateDelay;
            if (refreshScheduled) {
                scheduleNextRefresh();
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
    }

    private void scanExternalStorageMedia() {
        String[] paths = {
            "/storage/emulated/0/",
            "/sdcard/",
            "/sdcard/DCIM/",
            "/storage/",
            "/mnt/"
        };
        MediaScannerConnection.scanFile(requireContext(), paths, null, (path, uri) -> {
            Log.d("MediaScan", "Scanned: " + path);
            if (uri != null) {
                Log.d("MediaScan", "Scan completed for: " + uri);
                handler.post(() -> {
                    if (hasImagePermission() && !hasMediaContent && !landscapePreparing) {
                        loadImage();
                    }
                });
            }
        });
    }

    private String getLocation(String gpsLocation, TextView tv) {
        if(!TextUtils.isEmpty(gpsLocation)){
            String ak = BuildConfig.BAIDU_MAP_AK;
            if (TextUtils.isEmpty(ak)) {
                Log.w("getLocation", "Baidu Map AK missing; skip reverse geocoding.");
                return null;
            }
            String url = "https://api.map.baidu.com/reverse_geocoding/v3/?ak=" + ak + "&output=json&coordtype=wgs84ll&language=zh-cn&location=" + gpsLocation;
            Log.i("getLocation",gpsLocation);
            HttpUtils.instance().httpGet(url, null, null, new HttpUtils.ICallback() {
                @Override
                public void onSuccess(String body) {
                    Log.i("getLocation", body);
                    GPSResult result= gson.fromJson(body, GPSResult.class);
                    if(result != null && result.getStatus() == 0 && result.getResult() != null){
                        String gpsStr = result.getResult().getFormatted_address();
                        if(!TextUtils.isEmpty(gpsStr)){
                            handler.post(() -> tv.setText(gpsStr));
                        }
                    }
                }

                @Override
                public void onFail(int code, String errmsg) {

                }
            });
        }
        return null;
    }

    private Bitmap decodeScaledBitmap(PicItem picItem, int imgWidth, int imgHeight){
        Bitmap bitmap = ImageUtils.LoadBitmap(picItem.path, imgWidth, imgHeight);
        if(bitmap == null){
            return null;
        }

        int orientation = picItem.orientation;
        if(orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL){
            return bitmap;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Matrix matrix = new Matrix();

        switch (orientation){
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return bitmap;
        }

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }


    //加载exif 判断方向
    /*
                1        2       3      4         5            6           7          8
              888888  888888      88  88      8888888888  88                  88  8888888888
              88          88      88  88      88  88      88  88          88  88      88  88
              8888      8888    8888  8888    88          8888888888  8888888888          88
              88          88      88  88
              88          88  888888  888888

     */
    private int landScapeValue(String imagePath){
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, opt);
        //Log.i("[isLandScape]", imagePath + " " + opt.outWidth + " " + opt.outHeight);

        ExifInterface exifInterface = null;
        int oriValue = -1;
        try {
            exifInterface = new ExifInterface(imagePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(exifInterface != null){
            oriValue = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Log.i("[isLandScape]", imagePath + " " + oriValue);
            return oriValue;
        }

//        if( oriValue > 0 && opt.outWidth > opt.outHeight){
//            return (oriValue >= 1 && oriValue <= 4)? oriValue: -1;
//        }else if(oriValue > 0 && opt.outWidth < opt.outHeight){
//            return (oriValue >= 5 && oriValue <= 8)? oriValue: -1;
//        }
        return 0;
    }
    private String getGpsLocation(String imagePath){
        ExifInterface exifInterface = null;
        try {
            exifInterface = new ExifInterface(imagePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(exifInterface != null){
            String lat = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String latref = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            String lng = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            String lngref = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
            if (!TextUtils.isEmpty(lat) && !TextUtils.isEmpty(lng)) {
                // 检查是否为无效的0坐标
                if ("0/1,0/1,0/10000".equals(lat) && "0/1,0/1,0/10000".equals(lng)) {
                    return null; // 无效GPS数据
                }
                String ret = "";
                if (latref != null && latref.equalsIgnoreCase("S")) {
                    ret += "-";
                }
                ret += score2dimensionality(lat);
                ret += ",";
                if (lngref != null && lngref.equalsIgnoreCase("W")) {
                    ret += "-";
                }
                ret += score2dimensionality(lng);
                return ret;
            }
        }
        return null;
    }

    /**
     * 将 112/1,58/1,390971/10000 格式的经纬度转换成 112.99434397362694格式
     */
    private String score2dimensionality(String string) {
        double dimensionality = 0.00;
        if (null==string){
            return "";
        }
        //用 ，将数值分成3份
        String[] split = string.split(",");
        for (int i = 0; i < split.length; i++) {
            String[] s = split[i].split("/");
            //用112/1得到度分秒数值
            double v = Double.parseDouble(s[0]) / Double.parseDouble(s[1]);
            //将分秒分别除以60和3600得到度，并将度分秒相加
            dimensionality=dimensionality+v/Math.pow(60,i);
        }
        return String.valueOf(dimensionality);
    }


    private void randLoadImage(ContentResolver cr, int pageCount, List<PicItem> imglist, boolean includeVideo, int desiredCount, boolean singleMode){
        Log.i("randLoadImage", "start");
        int selectPage = getRandNum(pageCount);
        Log.i("randLoadImage", "pageCount="+pageCount);
        Log.i("randLoadImage", "selectPage="+selectPage);

        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Video.VideoColumns.DURATION
        };

        String selection;
        if (includeVideo) {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        } else {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
        }

        if (Build.VERSION.SDK_INT >= 29) selection = "(" + selection + ") AND is_pending=0";
        Cursor cursor = null;
        try {
            cursor = cr.query(getMediaStoreUri(), projection, selection, null, MediaStore.Files.FileColumns.DATE_ADDED + " DESC");
        } catch (IllegalArgumentException e) {
            Log.e("FirstFragment", "Failed to query paged media", e);
        }
        if (cursor != null) {
            int id = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID);
            int data = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
            int mediaTypeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE);
            int targetOffset = selectPage * PAGE_SIZE;
            if (targetOffset < cursor.getCount() && cursor.moveToPosition(targetOffset)) {
                int fetched = 0;
                do {
                    String filePath = cursor.getString(data);
                    int mediaType = mediaTypeIndex >= 0 ? cursor.getInt(mediaTypeIndex) : MediaStore.Files.FileColumns.MEDIA_TYPE_NONE;
                    long itemId = cursor.getLong(id);
                    if (!FileUtils.isFileExist(filePath)) {
                        continue;
                    }
                    if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        if (includeVideo && imglist.size() < desiredCount && !listContains(imglist,filePath)
                                && getVideoCount(imglist) < MAX_VIDEO_SLOTS){
                            long duration = 0L;
                            int durationIndex = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION);
                            if (durationIndex >= 0) {
                                duration = cursor.getLong(durationIndex);
                            }
                            Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, itemId);
                            imglist.add(new PicItem(filePath, 1, null, true, uri, duration));
                        }
                    } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                        int oriVal = landScapeValue(filePath);
                        if(imglist.size() < desiredCount && !listContains(imglist,filePath)){
                            Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, itemId);
                            PicItem item = new PicItem(filePath, oriVal, null, false, uri, CHANGE_TIMER);
                            if (singleMode) {
                                populateMediaMetadata(item);
                                // Only accept landscape images after correcting EXIF orientation.
                                if (item.width <= item.height || item.height <= 0) {
                                    Log.i("randLoadImage", "Skipping non-landscape image in single mode: " + filePath);
                                    break;
                                }
                            }
                            item.gpsLocation = getGpsLocation(filePath);
                            imglist.add(item);
                        }
                    }
                    fetched++;
                } while (cursor.moveToNext() && fetched < PAGE_SIZE && imglist.size() < desiredCount);
            }
            do {
                // no-op, processed in loop above
            } while (false);
            cursor.close();
        }
        Log.i("randLoadImage", "result size="+imglist.size());
        for(PicItem item : imglist){
            Log.i("randLoadImage", "result="+item.path);
        }
        Log.i("randLoadImage", "end");
    }

    boolean listContains(List<PicItem>imglist, String imagePath){
        for(PicItem item : imglist){
            if(item.path.equals(imagePath)){
                return true;
            }
        }
        return false;
    }

    private int getVideoCount(List<PicItem> list) {
        int count = 0;
        for (PicItem item : list) {
            if (item.isVideo) {
                count++;
            }
        }
        return count;
    }

    private void loadChangeInterval() {
        CHANGE_TIMER = gallerySettings.getInterval();
        Log.i("FirstFragment", "Loaded change interval: " + CHANGE_TIMER + " ms");
    }

    private void loadDisplayMode() {
        currentDisplayMode = gallerySettings.getMode();
        showingSingleMode = DISPLAY_MODE_SINGLE.equals(currentDisplayMode);
    }

    private void refreshUploadedMedia() {
        if (binding == null || !isAdded() || !hasImagePermission()) return;
        singleModeItems.clear();
        twoPhotoPrefetch = null;
        singleModeIndex = 0;
        loadRetryCount = 0;
        Log.i("GalleryUpload", "Refreshing media after published image upload");
        loadImage();
    }

    private void applySavedSettings() {
        if (binding == null) return;
        long interval = gallerySettings.getInterval();
        String mode = gallerySettings.getMode();
        if (interval == CHANGE_TIMER && mode.equals(currentDisplayMode)) return;
        CHANGE_TIMER = interval;
        currentDisplayMode = mode;
        twoPhotoPrefetch = null;
        singleModeIndex = 0;
        handler.removeCallbacksAndMessages(null);
        if (hasImagePermission()) loadImage();
    }

    private void showSettingsDialog() {
        if (settingsDialog != null) settingsDialog.dismiss();
        settingsDialog = new SettingsDialog(requireContext(), ((MainActivity) requireActivity()).getWebSettings());
        settingsDialog.show();
    }

}
