package com.example.shakerecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class FileListActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var empty: TextView
    private var files: List<File> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_list)
        title = "录音文件"
        listView = findViewById(R.id.listView)
        empty = findViewById(R.id.tvEmpty)
        load()

        listView.setOnItemClickListener { _, _, pos, _ ->
            val f = files[pos]
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(i)
            } catch (e: Exception) {
                Toast.makeText(this, "没有可播放音频的App", Toast.LENGTH_SHORT).show()
            }
        }

        listView.setOnItemLongClickListener { _, _, pos, _ ->
            if (files[pos].delete()) {
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                load()
            }
            true
        }
    }

    private fun load() {
        val dir = File(getExternalFilesDir(null), "recordings")
        files = if (dir.exists()) {
            dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: listOf()
        } else listOf()

        if (files.isEmpty()) {
            empty.text = "还没有录音文件"
            listView.adapter = null
        } else {
            empty.text = "点按播放，长按删除"
            listView.adapter = ArrayAdapter(
                this, android.R.layout.simple_list_item_1,
                files.map { "${it.name}  (${it.length() / 1024} KB)" }
            )
        }
    }
}
