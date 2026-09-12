package com.example.shakerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShakeService : Service() {

    private lateinit var sensorManager: SensorManager
    private lateinit var detector: ShakeDetector
    private var running = false

    private var recorder: MediaRecorder? = null
    private var recording = false
    private var currentFile: String = ""

    companion object {
        const val CHANNEL_ID = "shake_recorder_channel"
        const val NOTIF_ID = 1
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        var isRecording = false
        var lastFile: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        detector = ShakeDetector { toggleRecord() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (recording) stopRecording() else stopSelf()
                return START_NOT_STICKY
            }
            else -> startListening()
        }
        return START_STICKY
    }

    private fun startListening() {
        if (running) return
        createChannel()
        startForeground(NOTIF_ID, buildNotification("监听中… 左右摇两次开始录音"))
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(detector, sensor, SensorManager.SENSOR_DELAY_GAME)
        running = true
    }

    private fun toggleRecord() {
        if (recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        if (!hasAudioPermission()) {
            updateNotification("无录音权限，无法录制")
            return
        }
        val dir = File(getExternalFilesDir(null), "recordings")
        if (!dir.exists()) dir.mkdirs()
        val name = "rec_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        currentFile = File(dir, name).absolutePath

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        try {
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentFile)
                prepare()
                start()
            }
            recording = true
            isRecording = true
            lastFile = currentFile
            vibrate(120)
            updateNotification("录音中… 再摇两次或点「停止」")
        } catch (e: Exception) {
            updateNotification("录音启动失败：${e.message}")
        }
    }

    private fun stopRecording() {
        if (!recording) return
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        recording = false
        isRecording = false
        vibrate(220)
        updateNotification("已保存：${File(currentFile).name}")
    }

    private fun hasAudioPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun vibrate(ms: Long) {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms)
            }
        } catch (_: Exception) {}
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, ShakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val viewIntent = PendingIntent.getActivity(
            this, 3,
            Intent(this, RecorderActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("摇晃录音")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(viewIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "摇晃录音服务", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (recording) stopRecording()
        if (running) {
            sensorManager.unregisterListener(detector)
            running = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
