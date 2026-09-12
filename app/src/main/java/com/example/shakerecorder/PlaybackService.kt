package com.example.shakerecorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

/**
 * 播放服务：后台播放 + 通知栏播控（播放/暂停/停止）。
 */
class PlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "playback_channel"
        const val NOTIF_ID = 2001
        const val ACTION_PLAY_PAUSE = "pp"
        const val ACTION_STOP = "stop"
        const val EXTRA_PATH = "path"
    }

    inner class LocalBinder : Binder() { fun getService() = this@PlaybackService }

    private val binder = LocalBinder()
    private var player: MediaPlayer? = null
    var path: String = ""

    fun isPlaying() = player?.isPlaying == true

    fun startPlay(p: String) {
        if (player == null) {
            path = p
            player = MediaPlayer().apply {
                setDataSource(p)
                prepare()
                start()
            }
            showNotification(true)
        } else if (!player!!.isPlaying) {
            player!!.start()
            showNotification(true)
        }
    }

    fun pause() {
        player?.pause()
        showNotification(false)
    }

    fun stopAndRelease() {
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
        stopForeground(true)
    }

    fun currentPosition() = player?.currentPosition ?: 0
    fun duration() = player?.duration ?: 0
    fun seekTo(ms: Int) = player?.seekTo(ms)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (isPlaying()) pause() else if (path.isNotEmpty()) startPlay(path)
            }
            ACTION_STOP -> { stopAndRelease(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun showNotification(playing: Boolean) {
        createChannel()
        val playPauseIntent = PendingIntent.getService(
            this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 3, Intent(this, PlayerActivity::class.java).putExtra("path", path),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("播放录音")
            .setContentText(File(path).name)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_play, if (playing) "暂停" else "播放", playPauseIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "录音播放", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopAndRelease()
    }
}
