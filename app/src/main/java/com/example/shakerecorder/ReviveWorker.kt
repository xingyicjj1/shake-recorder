package com.example.shakerecorder

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * 复活 Worker：系统回收进程后周期性把前台监听服务拉回来。
 * （WorkManager 最小周期 15 分钟，用于兜底，不是实时）
 */
class ReviveWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {

    override fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val i = android.content.Intent(ctx, ShakeService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
