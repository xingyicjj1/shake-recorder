package com.example.shakerecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val i = Intent(context, ShakeService::class.java).apply {
                action = ShakeService.ACTION_START
            }
            try { ContextCompat.startForegroundService(context, i) } catch (_: Exception) {}
        }
    }
}
