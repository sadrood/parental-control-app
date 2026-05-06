package com.example.parentalcontrol.security

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Build
import java.util.ArrayDeque

/**
 * 设置页面拦截器
 * 借鉴 Blocker 项目的 SettingsBlockManager
 * 
 * 防止儿童通过设置页面进行以下操作：
 * - 强制停止 App
 * - 清除 App 数据
 * - 关闭辅助功能
 * - 取消设备管理员权限
 * - 卸载 App
 */
class SettingsBlockManager(private val context: Context) {

    companion object {
        private const val TAG = "SettingsBlockManager"

        // 需要拦截的设置应用包名（覆盖主流 OEM）
        private val SETTINGS_PACKAGES = setOf(
            // AOSP / Pixel / Motorola
            "com.android.settings",
            // Samsung
            "com.samsung.android.settings",
            "com.samsung.android.settings.wifi",
            // Xiaomi / Poco / Redmi
            "com.miui.securitycenter",
            "com.miui.home",
            "com.xiaomi.mipicks",
            // OPPO / OnePlus / Realme
            "com.oplus.settings",
            "com.oplus.wirelesssettings",
            "com.coloros.settings",
            "com.oneplus.settings",
            // Vivo
            "com.vivo.settings",
            "com.vivo.securitymanager",
            // Huawei / Honor
            "com.huawei.systemmanager",
            "com.huawei.hifolder",
            // 其他
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )

        // 本应用包名
        const val TARGET_PACKAGE = "com.example.parentalcontrol"
    }

    /**
     * 检查当前事件是否需要拦截
     * @return true 表示需要拦截（执行返回操作）
     */
    fun shouldBlock(event: AccessibilityEvent): Boolean {
        val packageName = event.packageName?.toString() ?: return false

        // 不拦截本应用和系统 UI
        if (packageName == TARGET_PACKAGE) return false
        if (packageName == "com.android.systemui") return false
        if (packageName == "com.android.launcher" || packageName == "com.android.launcher3") return false

        // 检查是否进入了设置页面
        if (!isSettingsPackage(packageName)) return false

        // 扫描节点树判断具体威胁
        val rootNode = event.source ?: return false
        return try {
            val reason = scanForThreats(rootNode, packageName)
            Log.d(TAG, "设置页面检查: pkg=$packageName, threat=$reason")
            reason != null
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 扫描无障碍节点树，检测威胁
     */
    fun scanForThreats(rootNode: AccessibilityNodeInfo, currentPackageName: String): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        val processedNodes = mutableListOf<AccessibilityNodeInfo>()
        var visitedCount = 0
        val MAX_VISITED = 200

        try {
            while (queue.isNotEmpty() && visitedCount < MAX_VISITED) {
                val node = queue.poll() ?: continue
                processedNodes.add(node)
                visitedCount++

                val reason = checkNode(node, currentPackageName)
                if (reason != null) return reason

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
            return null
        } finally {
            for (node in processedNodes) {
                if (node != rootNode) {
                    try { node.recycle() } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * 检查单个节点是否包含威胁
     */
    private fun checkNode(node: AccessibilityNodeInfo, currentPackageName: String): String? {
        val text = node.text?.toString() ?: ""
        val viewId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.viewIdResourceName ?: ""
        } else {
            ""
        }
        val contentDesc = node.contentDescription?.toString() ?: ""

        // 合并所有可读文本
        val allText = "$text $contentDesc".lowercase()

        // ===== 1. 检测本应用的应用详情页 =====
        // 当在设置中看到本应用的名称，说明进入了应用详情页
        val isAppDetailPage = allText.contains("parentalcontrol") ||
                allText.contains("家长控制") ||
                allText.contains("家长控制中心")

        if (isAppDetailPage) {
            // 检测"强制停止"按钮
            if (allText.contains("force stop") || allText.contains("强制停止")) {
                return "FORCE_STOP"
            }

            // 检测"清除数据"按钮
            if (allText.contains("clear data") || allText.contains("清除数据") ||
                allText.contains("clear storage") || allText.contains("清除存储")) {
                return "CLEAR_DATA"
            }

            // 检测"卸载"按钮
            if (allText.contains("uninstall") || allText.contains("卸载")) {
                return "UNINSTALL"
            }

            // 如果在应用详情页，不管什么操作都拦截（保守策略）
            // 只有当明确是其他应用时才放行
            return "APP_DETAIL_PAGE"
        }

        // ===== 2. 检测辅助功能设置页面 =====
        if (allText.contains("accessibility") || allText.contains("辅助功能") ||
            allText.contains("installed services") || allText.contains("已安装的服务")) {
            // 如果同时看到本应用名称，说明在辅助功能列表中
            if (allText.contains("parentalcontrol") || allText.contains("家长控制")) {
                return "ACCESSIBILITY_SETTINGS"
            }
        }

        // ===== 3. 检测设备管理员设置页面 =====
        if (allText.contains("device admin") || allText.contains("设备管理器") ||
            allText.contains("device admin apps") || allText.contains("设备管理应用")) {
            if (allText.contains("parentalcontrol") || allText.contains("家长控制")) {
                return "DEVICE_ADMIN_SETTINGS"
            }
        }

        // ===== 4. 检测应用管理页面（可能在列表中看到本应用） =====
        // 检测 viewId 中包含 app_info 的情况
        if (viewId.contains("app_info") || viewId.contains("application_detail")) {
            if (allText.contains("parentalcontrol") || allText.contains("家长控制")) {
                return "APP_INFO"
            }
        }

        return null
    }

    /**
     * 判断是否为设置类应用
     */
    private fun isSettingsPackage(packageName: String): Boolean {
        return SETTINGS_PACKAGES.any { pkg ->
            packageName.contains(pkg.substringAfterLast("."), ignoreCase = true) ||
                    packageName == pkg
        }
    }
}
