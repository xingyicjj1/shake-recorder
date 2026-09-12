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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 播放页：页面内 MediaPlayer 播放（可靠，不依赖后台服务），
 * 真实波形解码显示，支持暂停/继续/拖动/分享/删除。
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var file: File
    private var player: MediaPlayer? = null
    private var playing = false
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

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

        val p = intent.getStringExtra("path") ?: run { finish(); return }
        file = File(p)
        if (!file.exists()) { Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show(); finish(); return }

        tvName = findViewById(R.id.tvName)
        tvTime = findViewById(R.id.tvTime)
        seekBar = findViewById(R.id.seekBar)
        btnPlay = findViewById(R.id.btnPlay)
        btnShare = findViewById(R.id.btnShare)
        btnDelete = findViewById(R.id.btnDelete)
        btnBack = findViewById(R.id.btnBack)
        waveform = findViewById<WaveformView>(R.id.waveform)

        tvName.text = file.name
        btnPlay.setImageResource(android.R.drawable.ic_media_play)

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
                if (fromUser && player != null) {
                    player!!.seekTo(prog)
                    updateTime()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        initPlayer()
    }

    private fun initPlayer() {
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    playing = false
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    seekBar.progress = 0
                    waveform.setProgress(0f)
                }
                prepare()
            }
            seekBar.max = player!!.duration
            tvTime.text = "00:00 / ${fmt(player!!.duration)}"
        } catch (e: Exception) {
            Toast.makeText(this, "无法播放：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggle() {
        val pl = player ?: return
        if (playing) {
            pl.pause()
            playing = false
            btnPlay.setImageResource(android.R.drawable.ic_media_play)
        } else {
            pl.start()
            playing = true
            btnPlay.setImageResource(android.R.drawable.ic_media_pause)
            tick()
        }
    }

    private fun updateTime() {
        val cur = player?.currentPosition ?: 0
        val tot = player?.duration ?: 0
        tvTime.text = "${fmt(cur)} / ${fmt(tot)}"
        if (tot > 0) waveform.setProgress(cur.toFloat() / tot.toFloat())
    }

    private fun tick() {
        handler.post(object : Runnable {
            override fun run() {
                if (playing && player != null) {
                    seekBar.progress = player!!.currentPosition
                    updateTime()
                    handler.postDelayed(this, 200)
                }
            }
        })
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
        executor.shutdown()
    }
}
