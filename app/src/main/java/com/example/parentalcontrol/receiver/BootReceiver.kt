package com.example.parentalcontrol.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.parentalcontrol.service.CloudSyncService
import com.example.parentalcontrol.service.MonitoringService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "设备启动完成，启动监控服务和云同步服务")

            val monitoringIntent = Intent(context, MonitoringService::class.java)
            val syncIntent = Intent(context, CloudSyncService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(monitoringIntent)
                context.startForegroundService(syncIntent)
            } else {
                context.startService(monitoringIntent)
                context.startService(syncIntent)
            }
        }
    }
}
