package com.netflixbar.gallery;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.netflixbar.gallery.entity.GPSResult;

import java.util.LinkedHashMap;
import java.util.Map;

final class TencentGeocoder {
    static final String URL = "https://apis.map.qq.com/ws/geocoder/v1/";
    private static final Gson GSON = new Gson();

    private TencentGeocoder() { }

    static Map<String, String> parameters(String key, String gpsLocation) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("key", key.trim());
        parameters.put("location", gpsLocation);
        // 照片 EXIF 为 WGS84（纬度,经度），不能按默认腾讯 GCJ-02 坐标解释。
        parameters.put("coord_type", "1");
        parameters.put("get_poi", "0");
        parameters.put("output", "json");
        return parameters;
    }

    static String parseAddress(String body) {
        try {
            GPSResult response = GSON.fromJson(body, GPSResult.class);
            if (response == null || !response.isSuccess() || response.getResult() == null) {
                return null;
            }
            String address = response.getResult().getAddress();
            return address == null || address.trim().isEmpty() ? null : address;
        } catch (JsonParseException e) {
            return null;
        }
    }
}
