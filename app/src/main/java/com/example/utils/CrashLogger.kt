package com.example.utils

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
  private const val TAG = "CrashLogger"
  private const val MAX_LOG_LINES = 200

  private val ringBuffer = ArrayDeque<String>(MAX_LOG_LINES)
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

  @Synchronized
  fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
    val timeStr = dateFormat.format(Date())
    val logLine = "[$timeStr] [$level/$tag] $message" +
        if (throwable != null) "\n" + Log.getStackTraceString(throwable) else ""

    if (ringBuffer.size >= MAX_LOG_LINES) {
      ringBuffer.removeFirst()
    }
    ringBuffer.addLast(logLine)

    when (level) {
      "E" -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
      "W" -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
      "I" -> Log.i(tag, message)
      else -> Log.d(tag, message)
    }
  }

  fun d(tag: String, message: String) = log("D", tag, message)
  fun i(tag: String, message: String) = log("I", tag, message)
  fun w(tag: String, message: String, throwable: Throwable? = null) = log("W", tag, message, throwable)
  fun e(tag: String, message: String, throwable: Throwable? = null) = log("E", tag, message, throwable)

  fun install(context: Context) {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        writeCrashReport(context, thread, throwable)
      } catch (e: Throwable) {
        Log.e(TAG, "Failed writing crash report", e)
      } finally {
        previousHandler?.uncaughtException(thread, throwable)
      }
    }

    checkApplicationExitInfo(context)
  }

  private fun checkApplicationExitInfo(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val exitList = am?.getHistoricalProcessExitReasons(context.packageName, 0, 10)
        if (!exitList.isNullOrEmpty()) {
          val crashDir = getCrashDir(context)
          val exitFile = File(crashDir, "last_exit_reasons.txt")
          val sb = StringBuilder()
          sb.append("=== Application Exit History (Checked: ${dateFormat.format(Date())}) ===\n")
          for (exit in exitList) {
            val reasonStr = when (exit.reason) {
              ApplicationExitInfo.REASON_CRASH -> "CRASH (Uncaught Exception)"
              ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE CRASH"
              ApplicationExitInfo.REASON_ANR -> "ANR (Application Not Responding)"
              ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW MEMORY KILL"
              ApplicationExitInfo.REASON_USER_REQUESTED -> "USER REQUESTED (App swiped/closed)"
              ApplicationExitInfo.REASON_USER_STOPPED -> "USER STOPPED"
              ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION FAILURE"
              ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION CHANGE"
              ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE RESOURCE USAGE"
              else -> "REASON_CODE_${exit.reason}"
            }
            sb.append("Timestamp: ${dateFormat.format(Date(exit.timestamp))}\n")
            sb.append("PID: ${exit.pid}, Status: ${exit.status}, Reason: $reasonStr\n")
            sb.append("Description: ${exit.description ?: "None"}\n")
            sb.append("Importance: ${exit.importance}\n\n")
          }
          exitFile.writeText(sb.toString())
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error checking ApplicationExitInfo", e)
      }
    }
  }

  @Synchronized
  private fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable) {
    val crashDir = getCrashDir(context)
    val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.txt")
    val sb = StringBuilder()
    sb.append("================ CRASH REPORT ================\n")
    sb.append("Timestamp: ${dateFormat.format(Date())} (${System.currentTimeMillis()})\n")
    sb.append("Build Stamp: ${BuildConfig.BUILD_STAMP}\n")
    sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})\n")
    sb.append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
    sb.append("Thread: ${thread.name} (ID: ${thread.id})\n\n")
    sb.append("EXCEPTION:\n")
    sb.append(Log.getStackTraceString(throwable))
    sb.append("\n\nLAST 200 LOG LINES:\n")
    for (line in ringBuffer) {
      sb.append(line).append("\n")
    }
    sb.append("================ END OF REPORT ================\n")
    crashFile.writeText(sb.toString())
  }

  fun getCrashDir(context: Context): File {
    return File(context.filesDir, "crash_logs").apply { mkdirs() }
  }

  fun getLogs(context: Context): List<File> {
    val dir = getCrashDir(context)
    val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") } ?: emptyList()
    return files.sortedByDescending { it.lastModified() }
  }

  fun clearLogs(context: Context) {
    val dir = getCrashDir(context)
    dir.listFiles()?.forEach { it.delete() }
  }
}
