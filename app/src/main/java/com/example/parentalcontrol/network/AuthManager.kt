package com.example.parentalcontrol.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.parentalcontrol.network.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 认证管理器
 * 管理用户登录状态和配对
 */
class AuthManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val PREF_NAME = "auth_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_ROLE = "role"
        private const val KEY_PAIRED_WITH = "paired_with"

        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val apiService: ApiService by lazy { ApiClient.getApiService() }

    data class User(
        val id: String,
        val role: String,
        val token: String,
        val pairedWith: String? = null
    )

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val userId = prefs.getString(KEY_USER_ID, null)
        val token = prefs.getString(KEY_TOKEN, null)
        val role = prefs.getString(KEY_ROLE, null)
        val pairedWith = prefs.getString(KEY_PAIRED_WITH, null)

        if (userId != null && token != null && role != null) {
            _currentUser.value = User(userId, role, token, pairedWith)
            _isPaired.value = pairedWith != null
            ApiClient.authToken = token
            Log.d(TAG, "会话已恢复: $userId, role=$role, paired=$pairedWith")
        }
    }

    /**
     * 匿名登录
     */
    suspend fun anonymousLogin(role: String): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始匿名登录, role=$role")

            val deviceInfo = DeviceInfo(
                brand = android.os.Build.BRAND,
                model = android.os.Build.MODEL,
                osVersion = android.os.Build.VERSION.RELEASE
            )

            val request = AnonymousLoginRequest(role, deviceInfo)
            val response = apiService.anonymousLogin(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body() ?: return@withContext Result.failure(Exception("响应体为空"))

                val userId = body.user?.id ?: return@withContext Result.failure(Exception("用户ID为空"))
                val role = body.user?.role ?: return@withContext Result.failure(Exception("角色为空"))
                val token = body.token ?: return@withContext Result.failure(Exception("令牌为空"))
                
                prefs.edit()
                    .putString(KEY_USER_ID, userId)
                    .putString(KEY_TOKEN, token)
                    .putString(KEY_ROLE, role)
                    .apply()

                val user = User(
                    id = userId,
                    role = role,
                    token = token
                )
                _currentUser.value = user
                _pairingCode.value = body.pairingCode
                ApiClient.authToken = token

                Log.d(TAG, "登录成功: $userId, role=$role, pairingCode=${body.pairingCode}")

                Result.success(LoginResult(
                    userId = userId,
                    token = token,
                    role = role,
                    pairingCode = body.pairingCode
                ))
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = response.body()?.error ?: response.message()
                Log.e(TAG, "登录失败: HTTP ${response.code()} - $errorMsg")
                Log.e(TAG, "响应原始内容: $errorBody")
                Result.failure(Exception("登录失败 (${response.code()}): $errorMsg"))
            }
        } catch (e: Exception) {
            val readableMsg = ApiClient.parseNetworkError(e)
            Log.e(TAG, "登录异常: ${e.message}")
            Result.failure(Exception(readableMsg))
        }
    }

    /**
     * 儿童端链接到家长
     * POST /api/pairing/link { code, childId }
     */
    suspend fun linkWithCode(code: String): Result<PairResult> = withContext(Dispatchers.IO) {
        try {
            val childId = getCurrentUserId() ?: return@withContext Result.failure(Exception("未登录"))
            Log.d(TAG, "儿童端链接, code=$code, childId=$childId")

            val response = apiService.linkDevice(LinkRequest(code, childId))

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!

                prefs.edit()
                    .putString(KEY_PAIRED_WITH, body.parentId)
                    .apply()

                _currentUser.value = _currentUser.value?.copy(pairedWith = body.parentId)
                _isPaired.value = true

                Log.d(TAG, "儿童链接成功: parentId=${body.parentId}")
                Result.success(PairResult(
                    familyId = "",
                    childId = childId
                ))
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = response.body()?.error ?: response.message()
                Log.e(TAG, "儿童链接失败: HTTP ${response.code()} - $errorMsg")
                Log.e(TAG, "响应原始内容: $errorBody")
                Result.failure(Exception("链接失败 (${response.code()}): $errorMsg"))
            }
        } catch (e: Exception) {
            val readableMsg = ApiClient.parseNetworkError(e)
            Log.e(TAG, "儿童链接异常: ${e.message}")
            Result.failure(Exception(readableMsg))
        }
    }

    /**
     * 家长端绑定儿童（使用儿童生成的配对码）
     * POST /api/pairing/bind { code, parentId }
     */
    suspend fun pairWithCode(code: String): Result<PairResult> = withContext(Dispatchers.IO) {
        try {
            val parentId = getCurrentUserId() ?: return@withContext Result.failure(Exception("未登录"))
            Log.d(TAG, "家长端配对, code=$code, parentId=$parentId")

            val response = apiService.pairDevice(BindRequest(code, parentId))

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!

                prefs.edit()
                    .putString(KEY_PAIRED_WITH, body.childId)
                    .apply()

                _currentUser.value = _currentUser.value?.copy(pairedWith = body.childId)
                _isPaired.value = true

                Log.d(TAG, "家长配对成功: childId=${body.childId}")
                Result.success(PairResult(
                    familyId = body.familyId ?: "",
                    childId = body.childId ?: ""
                ))
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = response.body()?.error ?: response.message()
                Log.e(TAG, "家长配对失败: HTTP ${response.code()} - $errorMsg")
                Log.e(TAG, "响应原始内容: $errorBody")
                Result.failure(Exception("配对失败 (${response.code()}): $errorMsg"))
            }
        } catch (e: Exception) {
            val readableMsg = ApiClient.parseNetworkError(e)
            Log.e(TAG, "家长配对异常: ${e.message}")
            Result.failure(Exception(readableMsg))
        }
    }

    fun signOut() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_TOKEN)
            .remove(KEY_ROLE)
            .remove(KEY_PAIRED_WITH)
            .apply()

        _currentUser.value = null
        _isPaired.value = false
        _pairingCode.value = null
        ApiClient.authToken = null

        Log.d(TAG, "已登出")
    }

    fun getCurrentUserId(): String? = _currentUser.value?.id
    fun getCurrentUserRole(): String? = _currentUser.value?.role
    fun getPairedWith(): String? = _currentUser.value?.pairedWith
    fun isLoggedIn(): Boolean = _currentUser.value != null
}

data class LoginResult(
    val userId: String,
    val token: String,
    val role: String,
    val pairingCode: String? = null
)

data class PairResult(
    val familyId: String,
    val childId: String
)
