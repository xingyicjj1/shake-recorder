package com.example.shakerecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class FileListActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var empty: TextView
    private lateinit var multiBar: View
    private lateinit var tvSelected: TextView
    private lateinit var btnSelectAll: Button
    private lateinit var btnShareMul: Button
    private lateinit var btnDeleteMul: Button
    private lateinit var btnCancelMul: Button

    private var files: List<File> = listOf()
    private val selected = mutableSetOf<Int>()
    private var multiMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_list)
        title = "录音文件"

        listView = findViewById(R.id.listView)
        empty = findViewById(R.id.tvEmpty)
        multiBar = findViewById(R.id.multiBar)
        tvSelected = findViewById(R.id.tvSelected)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnShareMul = findViewById(R.id.btnShareMul)
        btnDeleteMul = findViewById(R.id.btnDeleteMul)
        btnCancelMul = findViewById(R.id.btnCancelMul)

        load()

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            if (multiMode) { toggleSelect(pos); return@OnItemClickListener }
            // 单击：打开播放详情页
            val i = Intent(this, PlayerActivity::class.java)
            i.putExtra("path", files[pos].absolutePath)
            startActivity(i)
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            if (!multiMode) enterMultiMode()
            toggleSelect(pos)
            true
        }

        btnSelectAll.setOnClickListener {
            if (selected.size == files.size) selected.clear() else selected.addAll(files.indices)
            refresh()
        }
        btnShareMul.setOnClickListener { shareSelected() }
        btnDeleteMul.setOnClickListener { deleteSelected() }
        btnCancelMul.setOnClickListener { exitMultiMode() }
    }

    private fun toggleSelect(pos: Int) {
        if (pos in selected) selected.remove(pos) else selected.add(pos)
        if (selected.isEmpty()) exitMultiMode() else refresh()
    }

    private fun enterMultiMode() {
        multiMode = true
        multiBar.visibility = View.VISIBLE
        refresh()
    }

    private fun exitMultiMode() {
        multiMode = false
        selected.clear()
        multiBar.visibility = View.GONE
        refresh()
    }

    private fun refresh() {
        tvSelected.text = "已选 ${selected.size} 项"
        listView.adapter = object : ArrayAdapter<String>(
            this, R.layout.item_file, R.id.tv,
            files.map { "${it.name}  (${it.length() / 1024} KB)" }
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val cb = v.findViewById<android.widget.CheckBox>(R.id.cb)
                cb.visibility = if (multiMode) View.VISIBLE else View.GONE
                cb.isChecked = position in selected
                return v
            }
        }
    }

    private fun shareSelected() {
        if (selected.isEmpty()) return
        val uris = selected.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", files[it]) }
        val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "分享录音到"))
    }

    private fun deleteSelected() {
        var n = 0
        selected.map { files[it] }.forEach { if (it.delete()) n++ }
        Toast.makeText(this, "已删除 $n 项", Toast.LENGTH_SHORT).show()
        exitMultiMode()
        load()
    }

    private fun load() {
        val dir = File(getExternalFilesDir(null), "recordings")
        files = if (dir.exists()) {
            dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: listOf()
        } else listOf()

        if (files.isEmpty()) {
            empty.text = "还没有录音文件"
            listView.adapter = null
            multiBar.visibility = View.GONE
            multiMode = false
        } else {
            empty.text = "点按播放，长按可多选分享/删除"
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!multiMode) load()
    }
}
