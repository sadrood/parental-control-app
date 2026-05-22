package com.guardianstar.parent.network;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // 仪表盘聚合数据
    @GET("api/devices/{deviceId}/dashboard")
    Call<Map<String, Object>> getDashboard(@Path("deviceId") String deviceId);

    // 设备列表
    @GET("api/devices")
    Call<List<Map<String, Object>>> getDevices();

    // 单个设备详情
    @GET("api/devices/{deviceId}")
    Call<Map<String, Object>> getDevice(@Path("deviceId") String deviceId);

    // 应用限制列表
    @GET("api/devices/{deviceId}/limits")
    Call<List<Map<String, Object>>> getLimits(@Path("deviceId") String deviceId);

    // 使用统计
    @GET("api/devices/{deviceId}/usage")
    Call<List<Map<String, Object>>> getUsage(@Path("deviceId") String deviceId, @Query("date") String date);

    // 定时任务
    @GET("api/devices/{deviceId}/schedules")
    Call<List<Map<String, Object>>> getSchedules(@Path("deviceId") String deviceId);

    // 手动上报
    @POST("api/devices/{deviceId}/report")
    Call<Map<String, Object>> triggerReport(@Path("deviceId") String deviceId);

    // 绑定设备
    @POST("api/devices/bind")
    Call<Map<String, Object>> bindDevice(@Path("deviceId") String deviceId);

    // SOS 告警
    @GET("api/sos")
    Call<List<Map<String, Object>>> getSOSAlerts();

    // 删除设备
    @DELETE("api/devices/{deviceId}")
    Call<Map<String, Object>> deleteDevice(@Path("deviceId") String deviceId);
}
