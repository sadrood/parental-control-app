package com.example.parentalcontrol.util

import android.util.Log
import com.example.parentalcontrol.BuildConfig

object LogUtil {

    private const val MAX_TAG_LENGTH = 23

    fun v(tag: String, msg: String) {
        if (BuildConfig.ENABLE_DEBUG_LOG) {
            Log.v(truncateTag(tag), msg)
        }
    }

    fun d(tag: String, msg: String) {
        if (BuildConfig.ENABLE_DEBUG_LOG) {
            Log.d(truncateTag(tag), msg)
        }
    }

    fun i(tag: String, msg: String) {
        Log.i(truncateTag(tag), msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(truncateTag(tag), msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(truncateTag(tag), msg, tr)
    }

    private fun truncateTag(tag: String): String {
        return if (tag.length > MAX_TAG_LENGTH) tag.substring(0, MAX_TAG_LENGTH) else tag
    }
}
