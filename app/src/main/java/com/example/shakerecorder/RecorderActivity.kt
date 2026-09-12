package com.example.shakerecorder

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RecorderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorder)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnStop = findViewById<Button>(R.id.btnStop)
        tvStatus.text = if (ShakeService.isRecording) {
            "正在录音…\n再摇两次手机，或点下方按钮停止"
        } else {
            "当前未在录音\n返回桌面，左右交替摇晃两次手机即可开始"
        }
        btnStop.setOnClickListener {
            startService(Intent(this, ShakeService::class.java).setAction(ShakeService.ACTION_STOP))
            finish()
        }
    }
}
