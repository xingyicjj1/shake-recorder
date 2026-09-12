package com.example.shakerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ShakeService : Service() {

    private lateinit var sensorManager: SensorManager
    private lateinit var detector: ShakeDetector
    private var running = false

    companion object {
        const val CHANNEL_ID = "shake_recorder_channel"
        const val NOTIF_ID = 1
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        detector = ShakeDetector { launchRecorder() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> startListening()
        }
        return START_STICKY
    }

    private fun startListening() {
        if (running) return
        createChannel()
        startForeground(NOTIF_ID, buildNotification("正在监听摇晃…"))
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(detector, sensor, SensorManager.SENSOR_DELAY_GAME)
        running = true
    }

    private fun launchRecorder() {
        val intent = Intent(this, RecorderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("摇晃录音")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "摇晃录音服务", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (running) {
            sensorManager.unregisterListener(detector)
            running = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
