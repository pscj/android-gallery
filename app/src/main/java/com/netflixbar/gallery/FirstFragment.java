package com.netflixbar.gallery;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.media.MediaScannerConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Build;
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
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
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

    private static final boolean ENABLE_VIDEO = false;

    private FragmentFirstBinding binding;

    private int SCREEN_SHOW_COUNT = 3;
    private int PAGE_SIZE = 1;
    private long CHANGE_TIMER = 1000 * 60 * 10;
    private Handler handler = new Handler();
    private ActivityResultLauncher<String[]> storagePermissionLauncher;
    private final List<ExoPlayer> activePlayers = new ArrayList<>();
    private Gson gson = new Gson();
    private SettingsDialog settingsDialog;
    private static final String PREFS_NAME = "gallery_settings";
    private static final String KEY_CHANGE_INTERVAL = "change_interval";
    private GestureDetector gestureDetector;
    private boolean hasMediaContent = false;
    class PicItem{
        PicItem(String path, int ori, String gpsStr, boolean isVideo, Uri contentUri){
            this.path = path;
            this.orientation = ori;
            this.gpsLocation = gpsStr;
            this.isVideo = isVideo;
            this.contentUri = contentUri;
        }
        public String path;
        public int orientation;
        public String gpsLocation;
        public boolean isVideo;
        public Uri contentUri;
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
        ContentResolver cr = context.getContentResolver();
        boolean includeVideo = ENABLE_VIDEO && hasVideoPermission();
        String selection;
        if (includeVideo) {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        } else {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
        }
        Cursor cursor = cr.query(MediaStore.Files.getContentUri("external"), new String[]{MediaStore.Files.FileColumns._ID}, selection,
                null, null);
        if (cursor != null){
            int totalCount = cursor.getCount();
            cursor.close();

            int pageCount = totalCount / PAGE_SIZE;
            if(pageCount <=0 ) {
                hasMediaContent = false;
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
            ImageView[] imgViewList = {binding.imgview1, binding.imgview2, binding.imgview3};
            PlayerView[] playerViewList = {binding.videoView1, binding.videoView2, binding.videoView3};
            TextView[] tvList = {binding.pos1, binding.pos2, binding.pos3};
            for(int i=0; i<SCREEN_SHOW_COUNT; i++){
                ImageView imageView = imgViewList[i];
                PlayerView playerView = playerViewList[i];
                TextView labelView = tvList[i];
                if (i < imagelist.size()) {
                    PicItem item = imagelist.get(i);
                    Log.i("getLocation path:", item.path);
                    if (item.isVideo) {
                        showVideo(item, imageView, playerView, labelView);
                    } else {
                        showImage(item, imageView, playerView, labelView, dm.first / SCREEN_SHOW_COUNT, dm.second);
                    }
                } else {
                    hideMediaSlot(imageView, playerView, labelView);
                }

            }
            hasMediaContent = true;
//            Bitmap bitmap1 = ImageUtils.LoadBitmap(imagelist.get(0).path, dm.first / SCREEN_SHOW_COUNT, dm.second);
//            binding.imgview1.setImageBitmap(bitmap1);
//            Bitmap bitmap2 = ImageUtils.LoadBitmap(imagelist.get(1).path, dm.first / SCREEN_SHOW_COUNT, dm.second);
//            binding.imgview2.setImageBitmap(bitmap2);
//            Bitmap bitmap3 = ImageUtils.LoadBitmap(imagelist.get(2).path, dm.first / SCREEN_SHOW_COUNT, dm.second);
//            binding.imgview3.setImageBitmap(bitmap3);
//            Bitmap bitmap4 = ImageUtils.LoadBitmap(imagelist.get(3).path, dm.first / SCREEN_SHOW_COUNT, dm.second);
//            binding.imgview4.setImageBitmap(bitmap4);
        } else {
            hasMediaContent = false;
        }

        handler.postDelayed(() -> FirstFragment.this.loadImage(), CHANGE_TIMER);
    }

    private void showImage(PicItem item, ImageView imageView, PlayerView playerView, TextView labelView, int imgWidth, int imgHeight) {
        playerView.setPlayer(null);
        playerView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        Bitmap bmp = innerLoadImage(item, imgWidth, imgHeight);
        if (bmp != null) {
            imageView.setImageBitmap(bmp);
        } else {
            imageView.setImageDrawable(null);
        }
        labelView.setText("");
        if (isNetworkAvailable()) {
            getLocation(item.gpsLocation, labelView);
        } else {
            Log.w("FirstFragment", "Network unavailable; skip geocoding.");
        }
    }

    private void showVideo(PicItem item, ImageView imageView, PlayerView playerView, TextView labelView) {
        imageView.setImageDrawable(null);
        imageView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        ExoPlayer player = new ExoPlayer.Builder(requireContext()).build();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setMediaItem(MediaItem.fromUri(item.contentUri));
        player.prepare();
        player.play();
        playerView.setPlayer(player);
        labelView.setText("");
        activePlayers.add(player);
    }

    private void hideMediaSlot(ImageView imageView, PlayerView playerView, TextView labelView) {
        imageView.setImageDrawable(null);
        imageView.setVisibility(View.GONE);
        playerView.setPlayer(null);
        playerView.setVisibility(View.GONE);
        labelView.setText("");
    }

    private void releasePlayers() {
        for (ExoPlayer player : activePlayers) {
            player.release();
        }
        activePlayers.clear();
        if (binding != null) {
            binding.videoView1.setPlayer(null);
            binding.videoView2.setPlayer(null);
            binding.videoView3.setPlayer(null);
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

    Bitmap innerLoadImage(PicItem picItem, int imgWidth, int imgHeight){
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

        if(opt.outWidth > opt.outHeight){
            return (oriValue >= 1 && oriValue <= 4)? oriValue: -1;
        }else if(opt.outWidth < opt.outHeight){
            return (oriValue >= 5 && oriValue <= 8)? oriValue: -1;
        }
        return -1;
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
            if(!TextUtils.isEmpty(lat) && !TextUtils.isEmpty(lng)){
                String ret = "";
                if(latref.equalsIgnoreCase("S")){
                    ret += "-";
                }
                ret += score2dimensionality(lat);
                ret+=",";
                if(lngref.equalsIgnoreCase("W")){
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
                MediaStore.Files.FileColumns.MEDIA_TYPE
        };

        String selection;
        if (includeVideo) {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        } else {
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
        }

        String sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC limit " + PAGE_SIZE + " offset " + selectPage * PAGE_SIZE;
        Cursor cursor = cr.query(MediaStore.Files.getContentUri("external"), projection, selection, null, sortOrder);
        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID);
            int data = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
            int mediaTypeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE);
            do {
                String filePath = cursor.getString(data);
                int mediaType = mediaTypeIndex >= 0 ? cursor.getInt(mediaTypeIndex) : MediaStore.Files.FileColumns.MEDIA_TYPE_NONE;
                long itemId = cursor.getLong(id);
                if (!FileUtils.isFileExist(filePath)) {
                    continue;
                }
                if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    if (includeVideo && imglist.size() < SCREEN_SHOW_COUNT && !listContains(imglist,filePath)){
                        Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, itemId);
                        imglist.add(new PicItem(filePath, 1, null, true, uri));
                    }
                } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                    int oriVal = landScapeValue(filePath);
                    String gpsLocation = getGpsLocation(filePath);
                    if(oriVal > 0 && imglist.size() < SCREEN_SHOW_COUNT && !listContains(imglist,filePath)){
                        Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, itemId);
                        imglist.add(new PicItem(filePath, oriVal, gpsLocation, false, uri));
                    }
                }
            } while (cursor.moveToNext());
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