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
import android.os.Binder
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

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService(): ShakeService = this@ShakeService }

    private lateinit var sensorManager: SensorManager
    private lateinit var detector: ShakeDetector
    private var listening = false

    private var recorder: MediaRecorder? = null
    var isRecording = false
        private set
    var isPaused = false
        private set
    var currentFile: String = ""
        private set

    private var recordStart = 0L
    private var pausedAccum = 0L
    private var pauseStart = 0L

    companion object {
        const val CHANNEL_ID = "shake_recorder_channel"
        const val NOTIF_ID = 1
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        var lastSavedFile: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        detector = ShakeDetector { toggleRecord() }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { if (isRecording) stopAndSave() else stopSelf(); return START_NOT_STICKY }
            "pause" -> { pauseRecording(); return START_STICKY }
            "resume" -> { resumeRecording(); return START_STICKY }
            else -> startListening()
        }
        startListening()
        return START_STICKY
    }

    // 服务被系统杀死后重建时，确保重新注册传感器监听
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restart = Intent(applicationContext, ShakeService::class.java).apply {
            action = ACTION_START
        }
        try { startForegroundService(restart) } catch (_: Exception) {}
    }

    private fun startListening() {
        if (listening) return
        createChannel()
        startForeground(NOTIF_ID, buildNotification(if (isRecording) notifText() else "监听中… 左右摇两次开始录音"))
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(detector, sensor, SensorManager.SENSOR_DELAY_GAME)
        listening = true
    }

    private fun toggleRecord() {
        if (isRecording) stopAndSave() else startRecording()
    }

    fun startRecording() {
        if (!hasAudioPermission()) { updateNotification("无录音权限，无法录制"); return }
        val dir = File(getExternalFilesDir(null), "recordings")
        if (!dir.exists()) dir.mkdirs()
        val name = "rec_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        currentFile = File(dir, name).absolutePath
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
                   else @Suppress("DEPRECATION") MediaRecorder()
        try {
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentFile)
                prepare(); start()
            }
            isRecording = true; isPaused = false
            recordStart = System.currentTimeMillis(); pausedAccum = 0
            vibrate(120)
            updateNotification(notifText())
            openRecorderUi()
        } catch (e: Exception) {
            updateNotification("录音启动失败：${e.message}")
        }
    }

    fun pauseRecording() {
        if (!isRecording || isPaused) return
        try { recorder?.pause() } catch (_: Exception) {}
        isPaused = true; pauseStart = System.currentTimeMillis()
        updateNotification(notifText())
    }

    fun resumeRecording() {
        if (!isRecording || !isPaused) return
        try { recorder?.resume() } catch (_: Exception) {}
        isPaused = false; pausedAccum += System.currentTimeMillis() - pauseStart
        updateNotification(notifText())
    }

    fun stopAndSave() {
        if (!isRecording) return
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        isRecording = false; isPaused = false
        lastSavedFile = currentFile
        vibrate(220)
        updateNotification("已保存：${File(currentFile).name}")
    }

    fun elapsedMs(): Long {
        if (!isRecording) return 0
        val now = System.currentTimeMillis()
        var ms = now - recordStart - pausedAccum
        if (isPaused) ms -= (now - pauseStart)
        return ms.coerceAtLeast(0)
    }

    private fun notifText(): String =
        if (isPaused) "已暂停 — 点通知继续/停止" else "录音中… 再摇两次或点「停止」"

    private fun openRecorderUi() {
        val i = Intent(this, RecorderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun hasAudioPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun vibrate(ms: Long) {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (_: Exception) {}
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 3, Intent(this, RecorderActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val actions = mutableListOf<NotificationCompat.Action>()
        if (isRecording) {
            val toggle = if (isPaused) "继续" to android.R.drawable.ic_media_play
                         else "暂停" to android.R.drawable.ic_media_pause
            val toggleIntent = PendingIntent.getService(this, 10,
                Intent(this, ShakeService::class.java).setAction(if (isPaused) "resume" else "pause"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            actions.add(NotificationCompat.Action(toggle.second, toggle.first, toggleIntent))
            val stopIntent = PendingIntent.getService(this, 2,
                Intent(this, ShakeService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            actions.add(NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent))
        }
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("摇晃录音").setContentText(text)
            .setSmallIcon(R.drawable.ic_mic).setContentIntent(pi)
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW)
        actions.forEach { b.addAction(it) }
        return b.build()
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
        if (isRecording) stopAndSave()
        if (listening) { sensorManager.unregisterListener(detector); listening = false }
    }
}
