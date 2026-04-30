package com.example.parentalcontrol.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 安全检测器
 * 借鉴 CST + Blocker + RASP-Android 的防绕过机制
 * 
 * 功能：
 * - Root 检测
 * - ADB/USB 调试检测
 * - 开发者选项检测
 * - 安全模式检测
 * - 模拟器检测
 * - VPN/代理检测
 */
object SecurityChecker {

    private const val TAG = "SecurityChecker"

    // ==================== Root 检测 ====================

    /**
     * 综合检测设备是否 Root
     */
    fun isDeviceRooted(): Boolean {
        return checkRootByBinary() || checkRootByPackages() || checkRootBySystemProps()
    }

    /** 通过检测 su 二进制文件 */
    private fun checkRootByBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/su/bin/su",
            "/magisk/.core/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    /** 通过检测 Root 管理应用 */
    private fun checkRootByPackages(): Boolean {
        val packages = arrayOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "de.robv.android.xposed.installer",
            "org.lsposed.manager"
        )
        return try {
            packages.any { pkg ->
                try {
                    File("/data/data/$pkg").exists()
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 通过系统属性检测 */
    private fun checkRootBySystemProps(): Boolean {
        return try {
            val buildTags = Build.TAGS
            buildTags != null && buildTags.contains("test-keys")
        } catch (e: Exception) {
            false
        }
    }

    // ==================== ADB/调试检测 ====================

    /**
     * 检测 USB 调试是否开启
     */
    fun isAdbEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED, 0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检测 WiFi ADB 是否开启（检测 5555 端口）
     */
    fun isAdbOverWifiEnabled(): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("localhost", 5555), 1000)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 开发者选项检测 ====================

    /**
     * 检测开发者选项是否开启
     */
    fun isDeveloperModeEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
            ) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检测模拟位置是否开启
     */
    fun isMockLocationEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                "mock_location"
            ) == "1"
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 安全模式检测 ====================

    /**
     * 检测设备是否处于安全模式
     */
    fun isSafeMode(): Boolean {
        // 方法一：检查系统属性
        val isSafeModeByProp = try {
            val prop = Class.forName("android.os.SystemProperties")
            val method = prop.getMethod("get", String::class.java)
            val value = method.invoke(null, "persist.sys.safemode") as? String
            value == "1"
        } catch (e: Exception) {
            false
        }

        // 方法二：检查文本渲染器
        val isSafeModeByText = try {
            val prop = Class.forName("android.os.SystemProperties")
            val method = prop.getMethod("get", String::class.java)
            val value = method.invoke(null, "ro.bootmode") as? String ?: ""
            value.contains("safe")
        } catch (e: Exception) {
            false
        }

        return isSafeModeByProp || isSafeModeByText
    }

    // ==================== 模拟器检测 ====================

    /**
     * 检测是否运行在模拟器中
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.contains("emulator")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || "google_sdk" == Build.PRODUCT)
    }

    // ==================== Frida/调试工具检测 ====================

    /**
     * 检测 Frida 相关进程
     */
    fun isFridaRunning(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("ps -A")
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.any { line ->
                    line.contains("frida", ignoreCase = true) ||
                    line.contains("frida-server", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 综合安全检查 ====================

    /**
     * 执行全面安全检查
     * @return 安全威胁列表
     */
    fun performSecurityCheck(context: Context): List<SecurityThreat> {
        val threats = mutableListOf<SecurityThreat>()

        if (isDeviceRooted()) {
            threats.add(SecurityThreat.ROOT_DETECTED)
            Log.w(TAG, "安全威胁: 设备已 Root")
        }

        if (isAdbEnabled(context)) {
            threats.add(SecurityThreat.ADB_ENABLED)
            Log.w(TAG, "安全威胁: USB 调试已开启")
        }

        if (isDeveloperModeEnabled(context)) {
            threats.add(SecurityThreat.DEVELOPER_MODE)
            Log.w(TAG, "安全威胁: 开发者选项已开启")
        }

        if (isSafeMode()) {
            threats.add(SecurityThreat.SAFE_MODE)
            Log.w(TAG, "安全威胁: 安全模式")
        }

        if (isFridaRunning()) {
            threats.add(SecurityThreat.FRIDA_DETECTED)
            Log.w(TAG, "安全威胁: Frida 检测到")
        }

        return threats
    }

    /**
     * 检查是否有严重安全威胁
     */
    fun hasCriticalThreats(context: Context): Boolean {
        val threats = performSecurityCheck(context)
        return threats.any { it.isCritical }
    }
}

/**
 * 安全威胁类型
 */
enum class SecurityThreat(val isCritical: Boolean, val description: String) {
    ROOT_DETECTED(true, "设备已 Root，安全性降低"),
    ADB_ENABLED(true, "USB 调试已开启"),
    DEVELOPER_MODE(false, "开发者选项已开启"),
    SAFE_MODE(true, "安全模式已激活"),
    FRIDA_DETECTED(true, "检测到调试工具"),
    EMULATOR_DETECTED(false, "运行在模拟器中"),
    MOCK_LOCATION(false, "模拟位置已开启")
}
