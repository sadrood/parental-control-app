package com.example.parentalcontrol.util

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("parental_control_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DAILY_TIME_LIMIT = "daily_time_limit"
        private const val KEY_DIFF_WEEKDAY_WEEKEND = "diff_weekday_weekend"
        private const val KEY_WEEKEND_TIME_LIMIT = "weekend_time_limit"
        private const val KEY_WEB_FILTER_ENABLED = "web_filter_enabled"
        private const val KEY_YOUTH_MODE_ENABLED = "youth_mode_enabled"
        private const val KEY_ANTI_UNINSTALL = "anti_uninstall"
        private const val KEY_ANTI_ROOT = "anti_root"
        private const val KEY_TIME_TAMPER_PROOF = "time_tamper_proof"
        private const val KEY_SCREENSHOT_AUDIT = "screenshot_audit"
        private const val KEY_STUDY_MODE = "study_mode"
        private const val KEY_CHILD_NAME = "child_name"
        private const val KEY_PARENT_NAME = "parent_name"
        private const val KEY_PARENT_PHONE = "parent_phone"
        private const val KEY_IS_SETUP_COMPLETE = "is_setup_complete"
        private const val KEY_SECURITY_SCORE = "security_score"
    }

    var dailyTimeLimit: Int
        get() = prefs.getInt(KEY_DAILY_TIME_LIMIT, 60) // 默认60分钟
        set(value) = prefs.edit().putInt(KEY_DAILY_TIME_LIMIT, value).apply()

    var diffWeekdayWeekend: Boolean
        get() = prefs.getBoolean(KEY_DIFF_WEEKDAY_WEEKEND, false)
        set(value) = prefs.edit().putBoolean(KEY_DIFF_WEEKDAY_WEEKEND, value).apply()

    var weekendTimeLimit: Int
        get() = prefs.getInt(KEY_WEEKEND_TIME_LIMIT, 120)
        set(value) = prefs.edit().putInt(KEY_WEEKEND_TIME_LIMIT, value).apply()

    var webFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEB_FILTER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WEB_FILTER_ENABLED, value).apply()

    var youthModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_YOUTH_MODE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_YOUTH_MODE_ENABLED, value).apply()

    var antiUninstall: Boolean
        get() = prefs.getBoolean(KEY_ANTI_UNINSTALL, true)
        set(value) = prefs.edit().putBoolean(KEY_ANTI_UNINSTALL, value).apply()

    var antiRoot: Boolean
        get() = prefs.getBoolean(KEY_ANTI_ROOT, true)
        set(value) = prefs.edit().putBoolean(KEY_ANTI_ROOT, value).apply()

    var timeTamperProof: Boolean
        get() = prefs.getBoolean(KEY_TIME_TAMPER_PROOF, true)
        set(value) = prefs.edit().putBoolean(KEY_TIME_TAMPER_PROOF, value).apply()

    var screenshotAudit: Boolean
        get() = prefs.getBoolean(KEY_SCREENSHOT_AUDIT, false)
        set(value) = prefs.edit().putBoolean(KEY_SCREENSHOT_AUDIT, value).apply()

    var studyMode: Boolean
        get() = prefs.getBoolean(KEY_STUDY_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_STUDY_MODE, value).apply()

    var childName: String
        get() = prefs.getString(KEY_CHILD_NAME, "小明") ?: "小明"
        set(value) = prefs.edit().putString(KEY_CHILD_NAME, value).apply()

    var parentName: String
        get() = prefs.getString(KEY_PARENT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PARENT_NAME, value).apply()

    var parentPhone: String
        get() = prefs.getString(KEY_PARENT_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PARENT_PHONE, value).apply()

    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_IS_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_SETUP_COMPLETE, value).apply()

    var securityScore: Int
        get() = prefs.getInt(KEY_SECURITY_SCORE, 98)
        set(value) = prefs.edit().putInt(KEY_SECURITY_SCORE, value).apply()
}
