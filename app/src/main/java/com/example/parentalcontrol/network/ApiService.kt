package com.example.parentalcontrol.network

import com.example.parentalcontrol.network.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * API 服务接口
 * 定义所有后端 API 端点
 */
interface ApiService {

    // ==================== 认证相关 ====================

    @POST("api/auth/anonymous")
    suspend fun anonymousLogin(@Body request: AnonymousLoginRequest): Response<AnonymousLoginResponse>

    @POST("api/pairing/generate")
    suspend fun generatePairingCode(@Body request: GeneratePairingCodeRequest): Response<PairingCodeResponse>

    @POST("api/pairing/bind")
    suspend fun pairDevice(@Body request: BindRequest): Response<PairResponse>

    @POST("api/pairing/link")
    suspend fun linkDevice(@Body request: LinkRequest): Response<PairResponse>

    // ==================== 应用规则 ====================

    @GET("api/rules/apps")
    suspend fun getAppRules(): Response<AppRulesResponse>

    @POST("api/rules/apps")
    suspend fun updateAppRule(@Body request: AppRuleRequest): Response<UpdateRuleResponse>

    @POST("api/rules/apps/batch")
    suspend fun batchUpdateAppRules(@Body request: BatchAppRulesRequest): Response<BatchUpdateResponse>

    @DELETE("api/rules/apps/{packageName}")
    suspend fun deleteAppRule(@Path("packageName") packageName: String): Response<BaseResponse>

    // ==================== 时间规则 ====================

    @GET("api/rules/time")
    suspend fun getTimeRules(): Response<TimeRulesResponse>

    @POST("api/rules/time")
    suspend fun addTimeRule(@Body request: TimeRuleRequest): Response<AddTimeRuleResponse>

    // ==================== 使用记录 ====================

    @POST("api/records/usage")
    suspend fun uploadUsageRecords(@Body request: UploadUsageRequest): Response<UploadResponse>

    @GET("api/records/usage")
    suspend fun getUsageRecords(
        @Query("date") date: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<UsageRecordsResponse>

    // ==================== 安全事件 ====================

    @POST("api/events/security")
    suspend fun reportSecurityEvent(@Body request: SecurityEventRequest): Response<SecurityEventResponse>

    @GET("api/events/security")
    suspend fun getSecurityEvents(@Query("limit") limit: Int = 50): Response<SecurityEventsResponse>

    // ==================== 远程控制 ====================

    @POST("api/control/lock-screen")
    suspend fun lockScreen(@Body request: LockScreenRequest): Response<BaseResponse>

    @POST("api/control/unlock-screen")
    suspend fun unlockScreen(@Body request: LockScreenRequest): Response<BaseResponse>

    @POST("api/control/sync")
    suspend fun requestSync(): Response<BaseResponse>
}
