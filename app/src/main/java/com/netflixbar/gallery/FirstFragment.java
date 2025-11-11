package com.netflixbar.gallery;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.ActivityInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import com.google.android.exoplayer2.Format;
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
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.Random;

public class FirstFragment extends Fragment {

    private static final boolean ENABLE_VIDEO = true;

    private FragmentFirstBinding binding;

    private static final int GRID_COLUMNS = 2;
    private static final int GRID_ROWS = 2;
    private static final int MAX_VIDEO_SLOTS = 1;
    private int SCREEN_SHOW_COUNT = GRID_COLUMNS * GRID_ROWS;
    private int PAGE_SIZE = 1;
    private long CHANGE_TIMER = 1000 * 60 * 10;
    private static final int VIDEO_BUFFER_MS = 3000;
    private static final int VIDEO_BACKBUFFER_MS = 1000;
    private Handler handler = new Handler();
    private ActivityResultLauncher<String[]> storagePermissionLauncher;
    private final List<ExoPlayer> activePlayers = new ArrayList<>();
    private Gson gson = new Gson();
    private SettingsDialog settingsDialog;
    private static final String PREFS_NAME = "gallery_settings";
    private static final String KEY_CHANGE_INTERVAL = "change_interval";
    private GestureDetector gestureDetector;
    private boolean hasMediaContent = false;
    private long nextRefreshDelayMs = CHANGE_TIMER;
    private boolean refreshScheduled = false;
    private final Runnable refreshRunnable = this::loadImage;
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
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storagePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (hasImagePermission()) {
                loadImage();
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
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Load saved interval from preferences
        loadChangeInterval();
        
        // Setup long press gesture detector for settings
        gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                showSettingsDialog();
            }
        });
        
        // Set touch listener on root view
        view.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        
        scanExternalStorageMedia();
        if (hasImagePermission()) {
            loadImage();
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
        handler.removeCallbacksAndMessages(null);
        hasMediaContent = false;
        releasePlayers();
        super.onDestroyView();
        binding = null;
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
        releasePlayers();
        handler.removeCallbacks(refreshRunnable);
        refreshScheduled = false;
        nextRefreshDelayMs = CHANGE_TIMER;
        ContentResolver cr = context.getContentResolver();
        boolean includeVideo = ENABLE_VIDEO && hasVideoPermission();
        String selection;
        if (includeVideo) {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        } else {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
        }
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
                hasMediaContent = false;
                scheduleNextRefresh();
                return;
            }

            List<PicItem> imagelist= new ArrayList<>();
            int maxAttempts = pageCount > 0 ? pageCount * 2 : 10;
            int attempts = 0;
            while (imagelist.size() < SCREEN_SHOW_COUNT && attempts < maxAttempts) {
                randLoadImage(cr, pageCount, imagelist, includeVideo);
                attempts++;
            }
            if (imagelist.isEmpty()) {
                Log.w("FirstFragment", "No images available to display.");
                hasMediaContent = false;
                return;
            }

            Pair<Integer, Integer> dm = Utils.getScreenSize(getActivity());
            ImageView[] imgViewList = {binding.imgview1, binding.imgview2, binding.imgview3, binding.imgview4};
            PlayerView[] playerViewList = {binding.videoView1, binding.videoView2, binding.videoView3, binding.videoView4};
            int slotWidth = dm.first / GRID_COLUMNS;
            int slotHeight = dm.second / GRID_ROWS;
            for(int i=0; i<SCREEN_SHOW_COUNT; i++){
                ImageView imageView = imgViewList[i];
                PlayerView playerView = playerViewList[i];
                if (i < imagelist.size()) {
                    PicItem item = imagelist.get(i);
                    Log.i("getLocation path:", item.path);
                    if (item.isVideo) {
                        showVideo(item, imageView, playerView);
                    } else {
                        showImage(item, imageView, playerView, slotWidth, slotHeight);
                    }
                } else {
                    hideMediaSlot(imageView, playerView);
                }

            }
            hasMediaContent = true;
//            Bitmap bitmap1 = ImageUtils.LoadBitmap(imagelist.get(0).path, dm.first / SCREEN_SHOW_COUNT, dm.second);
//            binding.imgview1.setImageBitmap(bitmap1);
        } else {
            hasMediaContent = false;
        }

        scheduleNextRefresh();
    }

    private void hideImageLocation(ImageView imageView) {
        int viewId = imageView.getId();
        if (viewId == binding.imgview1.getId()) {
            binding.imageLocation1.setVisibility(View.GONE);
        } else if (viewId == binding.imgview2.getId()) {
            binding.imageLocation2.setVisibility(View.GONE);
        } else if (viewId == binding.imgview3.getId()) {
            binding.imageLocation3.setVisibility(View.GONE);
        } else if (viewId == binding.imgview4.getId()) {
            binding.imageLocation4.setVisibility(View.GONE);
        }
    }

    private void hideVideoInfo(PlayerView playerView) {
        int viewId = playerView.getId();
        if (viewId == binding.videoView1.getId()) {
            binding.videoInfo1.setVisibility(View.GONE);
        } else if (viewId == binding.videoView2.getId()) {
            binding.videoInfo2.setVisibility(View.GONE);
        } else if (viewId == binding.videoView3.getId()) {
            binding.videoInfo3.setVisibility(View.GONE);
        } else if (viewId == binding.videoView4.getId()) {
            binding.videoInfo4.setVisibility(View.GONE);
        }
    }

    private void showImage(PicItem item, ImageView imageView, PlayerView playerView, int imgWidth, int imgHeight) {
        playerView.setPlayer(null);
        playerView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        hideVideoInfo(playerView);
        Bitmap bmp = decodeScaledBitmap(item, imgWidth, imgHeight);
        if (bmp != null) {
            imageView.setImageBitmap(bmp);
        } else {
            imageView.setImageDrawable(null);
        }
        // 显示位置信息
        if (isNetworkAvailable()) {
            showImageLocation(item, imageView);
        } else {
            Log.w("FirstFragment", "Network unavailable; skip image geocoding.");
        }
    }

    private void hideMediaSlot(ImageView imageView, PlayerView playerView) {
        imageView.setImageDrawable(null);
        imageView.setVisibility(View.GONE);
        playerView.setPlayer(null);
        playerView.setVisibility(View.GONE);
        hideVideoInfo(playerView);
        hideImageLocation(imageView);
    }

    private void showVideo(PicItem item, ImageView imageView, PlayerView playerView) {
        imageView.setImageDrawable(null);
        imageView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        // API 29+ HDR 支持：设置窗口为 HDR 模式（仅当设备支持 HDR 时）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isHdrSupported()) {
            requireActivity().getWindow().addFlags(LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            requireActivity().getWindow().setColorMode(ActivityInfo.COLOR_MODE_HDR);
        }

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(requireContext())
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
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
                    updateVideoInfo(playerView, item, player);

                    // 延迟一点时间再次更新码率信息，因为有些信息可能需要时间加载
                    handler.postDelayed(() -> {
                        if (playerView.getPlayer() == player) { // 确保播放器还没变
                            updateVideoInfo(playerView, item, player);
                        }
                    }, 2000);
                }
            }

            @Override
            public void onTracksChanged(com.google.android.exoplayer2.Tracks tracks) {
                updateVideoInfo(playerView, item, player);
            }
        });
        player.prepare();
        long immediateDuration = player.getDuration();
        if (immediateDuration != C.TIME_UNSET) {
            extendRefreshIfNeeded(immediateDuration);
        }
        player.play();
        playerView.setPlayer(player);
        activePlayers.add(player);

        // 显示位置信息
//        if (isNetworkAvailable()) {
//            showVideoLocation(item, playerView);
//        } else {
//            Log.w("FirstFragment", "Network unavailable; skip video geocoding.");
//        }
    }

    private void updateVideoInfo(PlayerView playerView, PicItem item, ExoPlayer player) {
        String fileName = new java.io.File(item.path).getName();

        StringBuilder detailText = new StringBuilder();

        try {
            com.google.android.exoplayer2.Tracks tracks = player.getCurrentTracks();
            if (tracks != null) {
                for (com.google.android.exoplayer2.Tracks.Group group : tracks.getGroups()) {
                    if (group.getType() == com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO && group.isSelected()) {
                        // Get selected track index
                        int selectedIndex = -1;
                        for (int i = 0; i < group.length; i++) {
                            if (group.isTrackSelected(i)) {
                                selectedIndex = i;
                                break;
                            }
                        }
                        if (selectedIndex >= 0) {
                            Format format = group.getTrackFormat(selectedIndex);
                            if (format != null) {
                                String resolution = format.width + "x" + format.height;
                                String codec = format.sampleMimeType != null ? format.sampleMimeType : "Unknown";
                                String fps = format.frameRate != Format.NO_VALUE ?
                                        String.format("%.0f fps", format.frameRate) : "Unknown";

                                // 尝试多种方式获取码率
                                String bitrate = getVideoBitrate(format, item, player);

                                // 获取文件大小
                                String fileSize = getFileSize(item.path);

                                detailText.append(String.format("Res: %s %sp | Codec: %s | Bitrate: %s",
                                        resolution, fps, codec, bitrate));
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("FirstFragment", "Error getting video info", e);
            detailText.append("Unable to retrieve video info");
        }

        if (detailText.length() == 0) {
            detailText.append("Loading video info...");
        }

        int viewId = playerView.getId();
        if (viewId == binding.videoView1.getId()) {
            binding.videoTitle1.setText(fileName + " - ");
            binding.videoDetails1.setText(detailText.toString());
            binding.videoInfo1.setVisibility(View.VISIBLE);
        } else if (viewId == binding.videoView2.getId()) {
            binding.videoTitle2.setText(fileName + " - ");
            binding.videoDetails2.setText(detailText.toString());
            binding.videoInfo2.setVisibility(View.VISIBLE);
        } else if (viewId == binding.videoView3.getId()) {
            binding.videoTitle3.setText(fileName + " - ");
            binding.videoDetails3.setText(detailText.toString());
            binding.videoInfo3.setVisibility(View.VISIBLE);
        } else if (viewId == binding.videoView4.getId()) {
            binding.videoTitle4.setText(fileName + " - ");
            binding.videoDetails4.setText(detailText.toString());
            binding.videoInfo4.setVisibility(View.VISIBLE);
        }
    }

    private String getVideoBitrate(Format format, PicItem item, ExoPlayer player) {
        // 方法1: 直接从 Format 获取
        if (format.bitrate != Format.NO_VALUE) {
            return String.format("%.1f Mbps", format.bitrate / 1_000_000.0);
        }

        // 方法2: 从文件大小和 ExoPlayer 时长估算
        try {
            java.io.File videoFile = new java.io.File(item.path);
            long fileSizeBytes = videoFile.length();
            long durationMs = player.getDuration();

            // 如果 ExoPlayer 时长无效，使用 PicItem 时长
            if (durationMs <= 0 || durationMs == C.TIME_UNSET) {
                durationMs = item.durationMs;
            }

            // 方法3: 使用 MediaMetadataRetriever 获取时长
            if (durationMs <= 0) {
                durationMs = getVideoDurationFromMetadata(item.path);
            }

            Log.d("BitrateCalc", "File: " + item.path + ", Size: " + fileSizeBytes + " bytes, Duration: " + durationMs + " ms");

            if (fileSizeBytes > 0 && durationMs > 0) {
                // 计算平均码率 (bits per second)
                // 公式: (文件大小(bytes) * 8) / 时长(seconds) = bits per second
                long bitrateBps = (fileSizeBytes * 8) / (durationMs / 1000);
                Log.d("BitrateCalc", "Calculated bitrate: " + bitrateBps + " bps");
                return String.format("%.1f Mbps", bitrateBps / 1_000_000.0);
            }
        } catch (Exception e) {
            Log.w("FirstFragment", "Failed to calculate bitrate from file", e);
        }

        return "Unknown";
    }

    private long getVideoDurationFromMetadata(String filePath) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                return Long.parseLong(durationStr);
            }
        } catch (Exception e) {
            Log.w("FirstFragment", "Failed to get duration from metadata", e);
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return 0;
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

    private void showImageLocation(PicItem item, ImageView imageView) {
        int viewId = imageView.getId();
        TextView locationView = null;

        if (viewId == binding.imgview1.getId()) {
            locationView = binding.imageLocation1;
        } else if (viewId == binding.imgview2.getId()) {
            locationView = binding.imageLocation2;
        } else if (viewId == binding.imgview3.getId()) {
            locationView = binding.imageLocation3;
        } else if (viewId == binding.imgview4.getId()) {
            locationView = binding.imageLocation4;
        }

        if (!TextUtils.isEmpty(item.gpsLocation)) {
            locationView.setVisibility(View.VISIBLE);

            if (locationView != null && isNetworkAvailable()) {
                getLocation(item.gpsLocation, locationView);
            }
        }else{
            locationView.setVisibility(View.GONE);
        }
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
            binding.videoView1.setPlayer(null);
            binding.videoView2.setPlayer(null);
            binding.videoView3.setPlayer(null);
            binding.videoView4.setPlayer(null);
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
                    if (hasImagePermission() && !hasMediaContent) {
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
        if(picItem.orientation == 1){
            return bitmap;
        }else if(picItem.orientation == 3){ //180 degree
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Matrix matrix = new Matrix();
            matrix.preRotate(180);
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        }else if(picItem.orientation == 4){
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Matrix matrix = new Matrix();
            matrix.preRotate(180);
            matrix.postScale(-1f, 1f);
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        }
        return bitmap;
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
        }

        if( oriValue > 0 && opt.outWidth > opt.outHeight){
            return (oriValue >= 1 && oriValue <= 4)? oriValue: -1;
        }else if(oriValue > 0 && opt.outWidth < opt.outHeight){
            return (oriValue >= 5 && oriValue <= 8)? oriValue: -1;
        }
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


    private void randLoadImage(ContentResolver cr, int pageCount, List<PicItem> imglist, boolean includeVideo){
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
                        if (includeVideo && imglist.size() < SCREEN_SHOW_COUNT && !listContains(imglist,filePath)
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
                        String gpsLocation = getGpsLocation(filePath);
                        if(oriVal >= 0 && imglist.size() < SCREEN_SHOW_COUNT && !listContains(imglist,filePath)){
                            Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, itemId);
                            imglist.add(new PicItem(filePath, oriVal, gpsLocation, false, uri, CHANGE_TIMER));
                        }
                    }
                    fetched++;
                } while (cursor.moveToNext() && fetched < PAGE_SIZE && imglist.size() < SCREEN_SHOW_COUNT);
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
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        CHANGE_TIMER = preferences.getLong(KEY_CHANGE_INTERVAL, 1000 * 60 * 10);
        Log.i("FirstFragment", "Loaded change interval: " + CHANGE_TIMER + " ms");
    }

    private void showSettingsDialog() {
        settingsDialog = new SettingsDialog(requireContext(), newInterval -> {
            CHANGE_TIMER = newInterval;
            Log.i("FirstFragment", "Settings updated, new interval: " + CHANGE_TIMER + " ms");
            // Restart image loading with new interval
            handler.removeCallbacksAndMessages(null);
            // Schedule the next load with the new interval
            handler.postDelayed(() -> {
                if (hasImagePermission()) {
                    loadImage();
                }
            }, CHANGE_TIMER);
        });
        settingsDialog.show();
    }

}