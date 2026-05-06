package com.example.parentalcontrol.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 家长端专用日志工具
 * 双输出：Logcat (tag: ParentalControl_Parent) + 本地文件
 *
 * 日志格式: [yyyy-MM-dd HH:mm:ss.SSS][Module][LEVEL] message
 * 使用前需调用 ParentLogUtil.init(context)
 */
object ParentLogUtil {

    const val LOGCAT_TAG = "ParentalControl_Parent"

    @Volatile
    private var isInitialized = false
    @Volatile
    private var logDir: File? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val writerExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var currentLogFile: File? = null
    @Volatile
    private var currentDate: String? = null

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                logDir = File(context.getExternalFilesDir(null), "logs").also { dir ->
                    if (!dir.exists()) dir.mkdirs()
                }
                isInitialized = true
                writeLog("ParentLogUtil", "INFO", "家长端日志系统初始化 | logDir=$logDir")
            } catch (e: Exception) {
                logDir = File(context.filesDir, "logs").also { dir ->
                    if (!dir.exists()) dir.mkdirs()
                }
                isInitialized = true
            }
        }
    }

    private fun ensureInit() {
        if (!isInitialized) Log.w(LOGCAT_TAG, "ParentLogUtil 未初始化，仅输出到 Logcat")
    }

    private fun getLogFile(): File? {
        val dir = logDir ?: return null
        val today = dateFormat.format(Date())
        if (today != currentDate || currentLogFile == null) {
            synchronized(this) {
                if (today != currentDate || currentLogFile == null) {
                    currentDate = today
                    currentLogFile = File(dir, "parent_log_$today.txt")
                }
            }
        }
        return currentLogFile
    }

    private fun buildMessage(module: String, level: String, msg: String): String {
        val timestamp = timeFormat.format(Date())
        return "[$timestamp][$module][$level] $msg"
    }

    private fun writeLog(module: String, level: String, msg: String, tr: Throwable? = null) {
        ensureInit()
        val fullMsg = if (tr != null) {
            val sw = StringWriter()
            tr.printStackTrace(PrintWriter(sw))
            buildMessage(module, level, "$msg\n${sw.toString()}")
        } else {
            buildMessage(module, level, msg)
        }

        when (level) {
            "ERROR" -> Log.e(LOGCAT_TAG, fullMsg)
            "WARN" -> Log.w(LOGCAT_TAG, fullMsg)
            "INFO" -> Log.i(LOGCAT_TAG, fullMsg)
            else -> Log.d(LOGCAT_TAG, fullMsg)
        }

        writerExecutor.execute {
            try {
                val file = getLogFile() ?: return@execute
                FileWriter(file, true).use { fw ->
                    fw.write(fullMsg)
                    fw.write("\n")
                }
            } catch (_: Exception) {}
        }
    }

    fun d(module: String, msg: String) = writeLog(module, "DEBUG", msg)
    fun i(module: String, msg: String) = writeLog(module, "INFO", msg)
    fun w(module: String, msg: String) = writeLog(module, "WARN", msg)
    fun e(module: String, msg: String, tr: Throwable? = null) = writeLog(module, "ERROR", msg, tr)
}
