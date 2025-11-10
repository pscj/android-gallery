package com.netflixbar.gallery.utils;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Pair;

public class Utils {
    public static Pair<Integer, Integer> getScreenSize(Context ctx){
        DisplayMetrics dm = new DisplayMetrics();
        dm = ctx.getResources().getDisplayMetrics();
        int screenWidth =dm.widthPixels;
        int screenHeight =dm.heightPixels;
        return new Pair<>(screenWidth, screenHeight);
    }
}
