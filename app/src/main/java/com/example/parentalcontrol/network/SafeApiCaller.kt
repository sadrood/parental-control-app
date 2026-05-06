package com.example.parentalcontrol.network

import com.example.parentalcontrol.data.repository.RepositoryResult
import com.example.parentalcontrol.util.LogUtil
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object SafeApiCaller {

    private const val TAG = "SafeApiCaller"

    suspend fun <T> call(
        tag: String = TAG,
        block: suspend () -> Response<T>
    ): RepositoryResult<T> {
        return try {
            val response = block()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    RepositoryResult.Success(body)
                } else {
                    LogUtil.w(tag, "响应体为空: ${response.code()}")
                    RepositoryResult.Error("响应数据为空", response.code())
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "未知错误"
                LogUtil.w(tag, "请求失败: ${response.code()} $errorBody")
                RepositoryResult.Error("请求失败(${response.code()})", response.code())
            }
        } catch (e: SocketTimeoutException) {
            LogUtil.e(tag, "连接超时: ${e.message}", e)
            RepositoryResult.Error("连接超时，请检查网络")
        } catch (e: ConnectException) {
            LogUtil.e(tag, "连接被拒绝: ${e.message}", e)
            RepositoryResult.Error("无法连接到服务器")
        } catch (e: UnknownHostException) {
            LogUtil.e(tag, "DNS解析失败: ${e.message}", e)
            RepositoryResult.Error("网络不可用，请检查连接")
        } catch (e: Exception) {
            LogUtil.e(tag, "网络请求异常: ${e.message}", e)
            RepositoryResult.Error("网络错误: ${e.message}")
        }
    }
}
