package com.example.shakerecorder

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private var outputFile: String = ""
    private var recording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorder)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        btnStop.setOnClickListener {
            stopRecording()
            finish()
        }
        startRecording(tvStatus)
    }

    private fun startRecording(status: TextView) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            status.text = "缺少录音权限，无法录音"
            return
        }
        val dir = File(getExternalFilesDir(null), "recordings")
        if (!dir.exists()) dir.mkdirs()
        val name = "rec_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        outputFile = File(dir, name).absolutePath

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile)
            try {
                prepare()
                start()
                recording = true
                status.text = "正在录音…\n文件：\n$outputFile"
            } catch (e: Exception) {
                status.text = "录音启动失败：${e.message}"
            }
        }
    }

    private fun stopRecording() {
        if (!recording) return
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            recording = false
            Toast.makeText(this, "录音已保存", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "停止失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (recording) {
            try { recorder?.stop() } catch (_: Exception) {}
            recorder?.release()
            recorder = null
        }
    }
}
