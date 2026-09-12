package com.example.shakerecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
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
    private var longPressedFile: File? = null

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

        registerForContextMenu(listView)
    }

    override fun onCreateContextMenu(
        menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val info = menuInfo as? android.widget.AdapterView.AdapterContextMenuInfo ?: return
        longPressedFile = files.getOrNull(info.position) ?: return
        menu?.setHeaderTitle(longPressedFile?.name ?: "录音")
        menu?.add(0, 1, 0, "分享到其它应用")
        menu?.add(0, 2, 0, "删除")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val f = longPressedFile ?: return super.onContextItemSelected(item)
        return when (item.itemId) {
            1 -> { shareFile(f); true }
            2 -> {
                if (f.delete()) {
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    load()
                }
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun shareFile(f: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "分享录音到"))
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
            empty.text = "点按播放，长按可分享或删除"
            listView.adapter = ArrayAdapter(
                this, android.R.layout.simple_list_item_1,
                files.map { "${it.name}  (${it.length() / 1024} KB)" }
            )
        }
    }
}
