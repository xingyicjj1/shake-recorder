package com.example.shakerecorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    private lateinit var file: File
    private var playing = false
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var svc: PlaybackService? = null
    private var bound = false

    private lateinit var tvName: TextView
    private lateinit var tvTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: FloatingActionButton
    private lateinit var btnShare: ImageButton
    private lateinit var btnDelete: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var waveform: WaveformView

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            svc = (b as PlaybackService.LocalBinder).getService()
            bound = true
            updateUI()
        }
        override fun onServiceDisconnected(n: ComponentName?) { bound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        supportActionBar?.hide()

        val p = intent.getStringExtra("path") ?: run { finish(); return }
        file = File(p)

        tvName = findViewById(R.id.tvName)
        tvTime = findViewById(R.id.tvTime)
        seekBar = findViewById(R.id.seekBar)
        btnPlay = findViewById(R.id.btnPlay)
        btnShare = findViewById(R.id.btnShare)
        btnDelete = findViewById(R.id.btnDelete)
        btnBack = findViewById(R.id.btnBack)
        waveform = findViewById<WaveformView>(R.id.waveform)

        tvName.text = file.name
        btnPlay.setOnClickListener { toggle() }
        btnShare.setOnClickListener { share() }
        btnDelete.setOnClickListener { delete() }
        btnBack.setOnClickListener { finish() }

        // 真实波形：后台线程解码
        waveform.setAmplitudes(placeholderWave())
        executor.execute {
            val amps = AudioDecoder.decodeAmplitudes(file.absolutePath)
            runOnUiThread { waveform.setAmplitudes(amps ?: placeholderWave()) }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, prog: Int, fromUser: Boolean) {
                if (fromUser && bound) {
                    svc?.seekTo(prog)
                    updateUI()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, PlaybackService::class.java), conn, Context.BIND_AUTO_CREATE)
        handler.post(tick)
    }

    override fun onStop() {
        super.onStop()
        if (bound) { unbindService(conn); bound = false }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (bound) updateUI()
            handler.postDelayed(this, 200)
        }
    }

    private fun toggle() {
        if (!bound) return
        val s = svc ?: return
        if (s.isPlaying()) { s.pause(); playing = false }
        else {
            if (s.path != file.absolutePath) {
                s.stopAndRelease()
                startForegroundServiceCompat()
                s.startPlay(file.absolutePath)
            } else {
                s.startPlay(file.absolutePath)
            }
            playing = true
        }
        updateUI()
    }

    private fun startForegroundServiceCompat() {
        val i = Intent(this, PlaybackService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, i)
    }

    private fun updateUI() {
        val s = svc ?: return
        val dur = s.duration()
        val cur = s.currentPosition()
        if (dur > 0) {
            seekBar.max = dur
            seekBar.progress = cur
            tvTime.text = "${fmt(cur)} / ${fmt(dur)}"
            waveform.setProgress(cur.toFloat() / dur.toFloat())
        }
        val nowPlaying = s.isPlaying()
        btnPlay.setImageResource(
            if (nowPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun placeholderWave(): FloatArray {
        val n = 96
        val arr = FloatArray(n)
        for (i in 0 until n) {
            arr[i] = (0.3f + 0.5f * kotlin.math.sin(i.toFloat() / n * Math.PI).toFloat())
        }
        return arr
    }

    private fun fmt(ms: Int): String {
        val m = TimeUnit.MILLISECONDS.toMinutes(ms.toLong())
        val s = TimeUnit.MILLISECONDS.toSeconds(ms.toLong()) % 60
        return "%02d:%02d".format(m, s)
    }

    private fun share() {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "分享录音到"))
    }

    private fun delete() {
        svc?.stopAndRelease()
        if (file.delete()) Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
