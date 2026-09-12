import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnToggle = findViewById<Button>(R.id.btnToggle)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnFiles = findViewById<Button>(R.id.btnFiles)
        val btnBattery = findViewById<Button>(R.id.btnBattery)

        btnToggle.setOnClickListener {
            if (!hasPermissions()) {
                requestPermissions()
                return@setOnClickListener
            }
            startService()
            markOn(btnToggle, tvStatus)
        }

        btnFiles.setOnClickListener {
            startActivity(Intent(this, FileListActivity::class.java))
        }

        btnBattery.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        if (isIgnoringBattery()) {
            btnBattery.visibility = Button.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        val btnBattery = findViewById<Button>(R.id.btnBattery)
        if (isIgnoringBattery()) btnBattery.visibility = Button.GONE
    }

    private fun markOn(btn: Button, tv: TextView) {
        btn.isEnabled = false
        btn.text = "已在后台监听"
        tv.text = "状态：已开启，锁屏也能用。\n左右交替摇晃两次开始录音，再摇两次停止。"
    }

    private fun hasPermissions(): Boolean {
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        else true
        return audio && notif
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
    }

    private fun startService() {
        val intent = Intent(this, ShakeService::class.java).apply {
            action = ShakeService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun isIgnoringBattery(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(packageName) ?: false
        } else true
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startService()
            markOn(findViewById(R.id.btnToggle), findViewById(R.id.tvStatus))
        }
    }
}
