package com.guardianstar.parent.network;

import android.util.Log;

import com.guardianstar.parent.BuildConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static final String TAG = "ApiClient";
    private static final String BASE_URL = BuildConfig.BASE_URL;
    private static Retrofit retrofit;

    /** 快速检测服务器是否可达（TCP 连接测试，5 秒超时） */
    public static boolean isServerReachable() {
        String host = "139.9.176.191";
        int port = 3000;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "服务器不可达: " + host + ":" + port + " - " + e.getMessage());
            return false;
        }
    }

    /** 日志拦截器：记录完整请求/响应时间线 */
    private static class TimingInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            long startNs = System.nanoTime();

            Log.i(TAG, "→ " + request.method() + " " + request.url());
            if (request.body() != null && request.body().contentLength() > 0) {
                Log.d(TAG, "  请求体大小: " + request.body().contentLength() + " bytes");
            }

            Response response;
            try {
                response = chain.proceed(request);
            } catch (IOException e) {
                long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                Log.e(TAG, "✗ 请求失败 (" + ms + "ms): " + e.getMessage());
                throw e;
            }

            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            Log.i(TAG, "← " + response.code() + " (" + ms + "ms) " + request.url());
            return response;
        }
    }

    public static Retrofit getClient() {
        if (retrofit == null) {
            HttpLoggingInterceptor bodyLog = new HttpLoggingInterceptor(msg ->
                    Log.d(TAG, "[HTTP] " + msg));
            bodyLog.setLevel(BuildConfig.DEBUG
                    ? HttpLoggingInterceptor.Level.BODY
                    : HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .addInterceptor(new TimingInterceptor())
                    .addInterceptor(bodyLog)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            Log.i(TAG, "OkHttp 客户端已初始化, BASE_URL=" + BASE_URL);
        }
        return retrofit;
    }

    public static ApiService getApiService() {
        return getClient().create(ApiService.class);
    }
}
