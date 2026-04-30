package com.example.parentalcontrol.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * API 客户端
 * 负责与自建服务器通信
 */
object ApiClient {

    private const val TAG = "ApiClient"

    // 服务器地址 - 华为云服务器
    // HTTP API 端口: 3000
    // WebSocket 端口: 3001
    const val BASE_URL = "http://139.9.176.191:3000/"
    const val WS_URL = "http://139.9.176.191:3001/"

    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null

    // 当前用户的 Token
    var authToken: String? = null

    fun getApiService(): ApiService {
        if (apiService == null) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d(TAG, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor { chain ->
                    val requestBuilder = chain.request().newBuilder()
                    authToken?.let { token ->
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                    chain.proceed(requestBuilder.build())
                }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit!!.create(ApiService::class.java)
        }
        return apiService!!
    }

    /**
     * 检查网络连通性
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * 解析网络异常为可读信息
     */
    fun parseNetworkError(error: Throwable): String {
        return when (error) {
            is SocketTimeoutException -> {
                Log.e(TAG, "连接超时: ${error.message}")
                "连接超时，请检查网络或服务器是否正常运行"
            }
            is ConnectException -> {
                Log.e(TAG, "连接被拒绝: ${error.message}")
                "无法连接到服务器（${error.message}），请确认服务器地址和端口是否正确"
            }
            is UnknownHostException -> {
                Log.e(TAG, "DNS解析失败: ${error.message}")
                "DNS解析失败，请检查网络连接或服务器地址是否正确"
            }
            is java.net.NoRouteToHostException -> {
                Log.e(TAG, "无法路由到主机: ${error.message}")
                "无法路由到服务器，请检查防火墙或网络配置"
            }
            is javax.net.ssl.SSLHandshakeException -> {
                Log.e(TAG, "SSL握手失败: ${error.message}")
                "SSL证书验证失败，请检查服务器证书配置"
            }
            else -> {
                Log.e(TAG, "未知网络错误: ${error.message}", error)
                "网络错误: ${error.message}"
            }
        }
    }

    /**
     * 更新服务器地址（需在 Application 初始化前调用）
     */
    fun updateBaseUrl(url: String) {
        retrofit = null
        apiService = null
    }
}
