package com.netflixbar.gallery.utils;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpUtils {
    private static final String TAG = HttpUtils.class.getSimpleName();

    public interface ICallback {
        void onSuccess(String body);
        void onFail(int code, String errmsg);
    }

    private OkHttpClient okHttpClient = new OkHttpClient();
    private static volatile HttpUtils _instance = null;

    private HttpUtils() {
        okHttpClient = okHttpClient.newBuilder()
                .connectTimeout(50, TimeUnit.SECONDS)
                .readTimeout(50, TimeUnit.SECONDS)
                .writeTimeout(50, TimeUnit.SECONDS).build();
    }

    public static HttpUtils instance() {
        if (_instance == null) {
            synchronized (HttpUtils.class) {
                if (_instance == null) {
                    _instance = new HttpUtils();
                }
            }
        }
        return _instance;
    }

    public void httpGet(String url, Map<String,String> params, Map<String,String> headers, final ICallback callback) {
        HttpUrl.Builder httpBuilder = HttpUrl.parse(url).newBuilder();
        if (params != null) {
            for(Map.Entry<String, String> param : params.entrySet()) {
                httpBuilder.addQueryParameter(param.getKey(),param.getValue());
            }
        }
        Request request = null;
        Request.Builder reqBuilder = new Request.Builder().url(httpBuilder.build());
        if(headers != null){
            for(Map.Entry<String, String> param : headers.entrySet()) {
                reqBuilder.addHeader(param.getKey(),param.getValue());
            }
        }
        request = reqBuilder.build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, e.getMessage());
                if(callback != null){
                    callback.onFail(0, e.getMessage());
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String str = response.body().string();
                int code = response.code();
//                Log.i(TAG, "onResponse:" + str);
                if(callback != null) {
                    if (code == 200 || code == 204) {
                        callback.onSuccess(str);
                    } else {
                        callback.onFail(response.code(), str);
                    }
                }
            }
        });
    }

    public void httpPost(String url, Map<String,String> headers, String data, ICallback callback) {
        RequestBody requestBody = RequestBody.create(MediaType.parse("text/html;charset=utf-8"), data);
        Request request = null;
        Request.Builder reqBuilder = new Request.Builder().url(url).post(requestBody);
        if(headers != null){
            for(Map.Entry<String, String> param : headers.entrySet()) {
                reqBuilder.addHeader(param.getKey(),param.getValue());
            }
        }
        request = reqBuilder.build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, e.getMessage());
                if(callback != null){
                    callback.onFail(0, e.getMessage());
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String str = response.body().string();
                int code = response.code();
                Log.i(TAG, "onResponse:" + str);
                if(callback != null) {
                    if (code == 200 || code == 204) {
                        callback.onSuccess(str);
                    } else {
                        callback.onFail(response.code(), str);
                    }
                }
            }
        });
    }

    public void httpPut(String url, String data, ICallback callback) {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(JSON, data);

        Request request = new Request.Builder().url(url).put(body).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, e.getMessage());
                if(callback != null){
                    callback.onFail(0, e.getMessage());
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String str = response.body().string();
                int code = response.code();
                Log.i(TAG, "onResponse:" + str);
                if(callback != null) {
                    if (code == 200 || code == 204) {
                        callback.onSuccess(str);
                    } else {
                        callback.onFail(response.code(), str);
                    }
                }
            }
        });
    }
}
