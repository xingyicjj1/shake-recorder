package com.example.shakerecorder

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    private lateinit var file: File
    private var player: MediaPlayer? = null
    private var playing = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvName: TextView
    private lateinit var tvTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: FloatingActionButton
    private lateinit var btnShare: ImageButton
    private lateinit var btnDelete: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var waveform: WaveformView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        supportActionBar?.hide()

        val path = intent.getStringExtra("path") ?: run { finish(); return }
        file = File(path)

        tvName = findViewById(R.id.tvName)
        tvTime = findViewById(R.id.tvTime)
        seekBar = findViewById(R.id.seekBar)
        btnPlay = findViewById(R.id.btnPlay)
        btnShare = findViewById(R.id.btnShare)
        btnDelete = findViewById(R.id.btnDelete)
        btnBack = findViewById(R.id.btnBack)
        waveform = findViewById<WaveformView>(R.id.waveform)

        tvName.text = file.name
        waveform.setAmplitudes(generateMockWave(file.length()))

        btnPlay.setOnClickListener { toggle() }
        btnShare.setOnClickListener { share() }
        btnDelete.setOnClickListener { delete() }
        btnBack.setOnClickListener { finish() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser && player != null) {
                    player!!.seekTo(p)
                    updateTime()
                    val frac = if (seekBar.max > 0) p.toFloat() / seekBar.max.toFloat() else 0f
                    waveform.setProgress(frac)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun generateMockWave(len: Long): FloatArray {
        // 仅用于占位可视化：依据文件大小生成伪随机包络
        val n = 72
        val arr = FloatArray(n)
        var seed = (len xor 0x123456789L).let { if (it == 0L) 12345L else it }
        for (i in 0 until n) {
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            val base = (seed % 100) / 100f
            val env = kotlin.math.sin(i.toFloat() / n * Math.PI).toFloat()
            arr[i] = (0.25f + base * 0.75f) * (0.4f + env * 0.6f)
        }
        return arr
    }

    private fun toggle() {
        if (player == null) initPlayer()
        player ?: return
        if (playing) {
            player!!.pause()
            playing = false
            btnPlay.setImageResource(android.R.drawable.ic_media_play)
        } else {
            player!!.start()
            playing = true
            btnPlay.setImageResource(android.R.drawable.ic_media_pause)
            tick()
        }
    }

    private fun initPlayer() {
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
            seekBar.max = player!!.duration
            updateTime()
        } catch (e: Exception) {
            Toast.makeText(this, "无法播放：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTime() {
        val cur = player?.currentPosition ?: 0
        val tot = player?.duration ?: 0
        tvTime.text = "${fmt(cur)} / ${fmt(tot)}"
    }

    private fun tick() {
        handler.post(object : Runnable {
            override fun run() {
                if (playing && player != null) {
                    seekBar.progress = player!!.currentPosition
                    updateTime()
                    val frac = if (seekBar.max > 0) player!!.currentPosition.toFloat() / seekBar.max.toFloat() else 0f
                    waveform.setProgress(frac)
                    if (!player!!.isPlaying) {
                        playing = false
                        btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    }
                    handler.postDelayed(this, 200)
                }
            }
        })
    }

    private fun fmt(ms: Int): String {
        val m = TimeUnit.MILLISECONDS.toMinutes(ms.toLong())
        val s = TimeUnit.MILLISECONDS.toSeconds(ms.toLong()) % 60
        return "%02d:%02d".format(m, s)
    }

    private fun share() {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(i, "分享录音到"))
    }

    private fun delete() {
        try { player?.release() } catch (_: Exception) {}
        player = null
        if (file.delete()) Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
