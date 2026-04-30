package com.example.parentalcontrol.network.model

import com.google.gson.annotations.SerializedName

// ==================== 请求模型 ====================

data class AnonymousLoginRequest(
    val role: String,  // "parent" 或 "child"
    val deviceInfo: DeviceInfo? = null
)

data class DeviceInfo(
    val brand: String? = null,
    val model: String? = null,
    val osVersion: String? = null
)

data class PairRequest(
    val pairingCode: String
)

data class BindRequest(
    val code: String,
    val parentId: String
)

data class LinkRequest(
    val code: String,
    val childId: String
)

data class GeneratePairingCodeRequest(
    val deviceId: String,
    val role: String
)

data class AppRuleRequest(
    val packageName: String,
    val isBlocked: Boolean = false,
    val dailyLimitMinutes: Int = 0,
    val category: String = "other"
)

data class BatchAppRulesRequest(
    val rules: List<AppRuleRequest>
)

data class TimeRuleRequest(
    val startTime: String,
    val endTime: String,
    val daysOfWeek: List<Int>,
    val ruleType: String = "allowed"
)

data class UploadUsageRequest(
    val records: List<UsageRecordItem>
)

data class UsageRecordItem(
    val packageName: String,
    val date: String,
    val durationMinutes: Long,
    val sessionCount: Int = 1
)

data class SecurityEventRequest(
    val eventType: String,
    val description: String? = null,
    val severity: String = "info"
)

data class LockScreenRequest(
    val parentId: String,
    val duration: Int = 0
)

// ==================== 响应模型 ====================

data class BaseResponse(
    val success: Boolean,
    val error: String? = null
)

data class AnonymousLoginResponse(
    val success: Boolean,
    val user: UserInfo? = null,
    val token: String? = null,
    val pairingCode: String? = null,
    val error: String? = null
)

data class UserInfo(
    val id: String? = null,
    val role: String? = null
)

data class PairingCodeResponse(
    val success: Boolean,
    val pairingCode: String? = null,
    val error: String? = null
)

data class PairResponse(
    val success: Boolean,
    val familyId: String? = null,
    val parentId: String? = null,
    val childId: String? = null,
    val message: String? = null,
    val error: String? = null
)

data class AuthStatusResponse(
    val success: Boolean,
    val role: String? = null,
    val pairedWith: String? = null,
    val isPaired: Boolean = false,
    val error: String? = null
)

data class AppRulesResponse(
    val success: Boolean,
    val rules: List<AppRuleItem> = emptyList(),
    val error: String? = null
)

data class AppRuleItem(
    @SerializedName("id") val id: String,
    @SerializedName("familyId") val familyId: String,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("isBlocked") val isBlocked: Boolean,
    @SerializedName("dailyLimitMinutes") val dailyLimitMinutes: Int,
    @SerializedName("category") val category: String,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class UpdateRuleResponse(
    val success: Boolean,
    val ruleId: String? = null,
    val error: String? = null
)

data class BatchUpdateResponse(
    val success: Boolean,
    val count: Int = 0,
    val error: String? = null
)

data class TimeRulesResponse(
    val success: Boolean,
    val rules: List<TimeRuleItem> = emptyList(),
    val error: String? = null
)

data class TimeRuleItem(
    val id: String,
    val familyId: String,
    val startTime: String,
    val endTime: String,
    val daysOfWeek: String,
    val ruleType: String,
    val createdAt: String? = null
)

data class AddTimeRuleResponse(
    val success: Boolean,
    val ruleId: String? = null,
    val error: String? = null
)

data class UploadResponse(
    val success: Boolean,
    val count: Int = 0,
    val error: String? = null
)

data class UsageRecordsResponse(
    val success: Boolean,
    val records: List<UsageRecordData> = emptyList(),
    val error: String? = null
)

data class UsageRecordData(
    val id: String,
    val familyId: String,
    val packageName: String,
    val date: String,
    val totalDurationMinutes: Long,
    val sessionCount: Int,
    val createdAt: String? = null
)

data class SecurityEventResponse(
    val success: Boolean,
    val eventId: String? = null,
    val error: String? = null
)

data class SecurityEventsResponse(
    val success: Boolean,
    val events: List<SecurityEventData> = emptyList(),
    val error: String? = null
)

data class SecurityEventData(
    val id: String,
    val familyId: String,
    val eventType: String,
    val description: String?,
    val severity: String,
    val createdAt: String
)
