package com.example.shakerecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 设置页：集中引导后台保活相关的系统设置（荣耀/华为等 ROM 必需）
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var tvBatteryStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = "设置"

        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            if (isIgnoringBatteryOptimizations()) {
                Toast.makeText(this, "已加入电池优化白名单 ✓", Toast.LENGTH_SHORT).show()
            } else {
                @Suppress("DEPRECATION")
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    try { startActivity(i) } catch (_: Exception) { openAppDetails() }
                } else {
                    openAppDetails()
                }
            }
        }

        findViewById<Button>(R.id.btnAppDetails).setOnClickListener { openAppDetails() }

        findViewById<Button>(R.id.btnNotifSettings).setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                try { startActivity(i) } catch (_: Exception) { openAppDetails() }
            } else {
                openAppDetails()
            }
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        val ok = isIgnoringBatteryOptimizations()
        tvBatteryStatus.text = if (ok) "状态：已允许后台运行（已忽略电池优化）✓"
                               else "状态：未允许，后台可能被系统关闭 ✗"
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
    }

    private fun openAppDetails() {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        try { startActivity(i) } catch (_: Exception) {
            Toast.makeText(this, "无法打开设置页", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openHonorBackgroundPopup() {
        // 荣耀/华为：后台弹出界面权限（MIUI/oppo 等也有类似页）
        try {
            val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
        } catch (_: Exception) {}
    }
}
