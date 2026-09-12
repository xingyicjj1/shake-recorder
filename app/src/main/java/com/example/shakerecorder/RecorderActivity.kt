package com.example.shakerecorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.TimeUnit

class RecorderActivity : AppCompatActivity() {

    private var svc: ShakeService? = null
    private var bound = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvTimer: TextView
    private lateinit var tvState: TextView
    private lateinit var btnPause: Button
    private lateinit var btnStop: Button

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            svc = (binder as ShakeService.LocalBinder).getService()
            bound = true
            refresh()
            tick()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorder)

        tvTimer = findViewById(R.id.tvTimer)
        tvState = findViewById(R.id.tvState)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)

        btnPause.setOnClickListener {
            svc?.let {
                if (it.isPaused) it.resumeRecording() else it.pauseRecording()
                refresh()
            }
        }
        btnStop.setOnClickListener {
            svc?.stopAndSave()
            finish()
        }

        val i = Intent(this, ShakeService::class.java)
        bindService(i, conn, Context.BIND_AUTO_CREATE)
    }

    private fun fmt(ms: Long): String {
        val m = TimeUnit.MILLISECONDS.toMinutes(ms)
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        val cs = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(m, s, cs)
    }

    private fun refresh() {
        svc ?: return
        tvState.text = if (!svc!!.isRecording) "未在录音"
                       else if (svc!!.isPaused) "已暂停" else "录音中…"
        btnPause.text = if (svc!!.isPaused) "继续" else "暂停"
        btnPause.isEnabled = svc!!.isRecording
    }

    private fun tick() {
        handler.post(object : Runnable {
            override fun run() {
                if (bound && svc != null) {
                    tvTimer.text = fmt(svc!!.elapsedMs())
                    if (svc!!.isRecording) {
                        tvState.text = if (svc!!.isPaused) "已暂停" else "录音中…"
                        btnPause.text = if (svc!!.isPaused) "继续" else "暂停"
                        btnPause.isEnabled = true
                    }
                }
                handler.postDelayed(this, 50)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) { unbindService(conn); bound = false }
    }
}
