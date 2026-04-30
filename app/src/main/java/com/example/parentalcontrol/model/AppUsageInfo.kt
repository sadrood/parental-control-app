package com.example.parentalcontrol.model

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTime: Long, // 毫秒
    val icon: android.graphics.drawable.Drawable? = null
)
