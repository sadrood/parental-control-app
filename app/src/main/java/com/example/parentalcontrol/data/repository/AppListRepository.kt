package com.example.parentalcontrol.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.example.parentalcontrol.model.AppInfo

class AppListRepository(private val context: Context) {

    fun getInstalledApps(includeSystemApps: Boolean = false): List<AppInfo> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val activities = pm.queryIntentActivities(intent, 0)
        val apps = mutableListOf<AppInfo>()

        for (resolveInfo in activities) {
            if (!includeSystemApps) {
                try {
                    val appInfo = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0)
                    if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) {
                        continue
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            val appName = resolveInfo.loadLabel(pm).toString()
            val packageName = resolveInfo.activityInfo.packageName
            val icon = resolveInfo.loadIcon(pm)
            apps.add(AppInfo(appName, packageName, icon))
        }

        return apps.sortedBy { it.appName }
    }

    fun searchApps(query: String, includeSystemApps: Boolean = false): List<AppInfo> {
        return getInstalledApps(includeSystemApps).filter {
            it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }
}
