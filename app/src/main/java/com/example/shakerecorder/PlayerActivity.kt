package com.example.shakerecorder

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
    private lateinit var btnPlay: Button
    private lateinit var btnShare: Button
    private lateinit var btnDelete: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        title = "播放录音"

        val path = intent.getStringExtra("path") ?: run { finish(); return }
        file = File(path)

        tvName = findViewById(R.id.tvName)
        tvTime = findViewById(R.id.tvTime)
        seekBar = findViewById(R.id.seekBar)
        btnPlay = findViewById(R.id.btnPlay)
        btnShare = findViewById(R.id.btnShare)
        btnDelete = findViewById(R.id.btnDelete)

        tvName.text = file.name

        btnPlay.setOnClickListener { toggle() }
        btnShare.setOnClickListener { share() }
        btnDelete.setOnClickListener { delete() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser && player != null) {
                    player!!.seekTo(p)
                    updateTime()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun toggle() {
        if (player == null) initPlayer()
        player ?: return
        if (playing) {
            player!!.pause()
            playing = false
            btnPlay.text = "播放"
        } else {
            player!!.start()
            playing = true
            btnPlay.text = "暂停"
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
                    if (!player!!.isPlaying) {
                        playing = false
                        btnPlay.text = "播放"
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
