package com.pagegrab.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

private fun fmt(b: Long): String = when {
    b < 0 -> "SIZE ?"
    b < 1024 -> "$b B"
    b < 1048576 -> "${b / 1024} KB"
    else -> String.format("%.1f MB", b / 1048576.0)
}

class MainActivity : Activity() {

    private lateinit var urlBox: EditText
    private lateinit var status: TextView
    private lateinit var list: ListView
    private lateinit var btnScan: Button
    private lateinit var btnDl: Button
    private lateinit var scanner: PageScanner
    private var items: List<Item> = emptyList()

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun btn(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(36), dp(16), dp(12))
        }
        root.addView(TextView(this).apply {
            text = "PAGEGRAB"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
        })

        urlBox = EditText(this).apply {
            hint = "PASTE ANY LINK"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        urlBox.setOnEditorActionListener { _, _, _ -> startScan(); true }
        root.addView(urlBox)

        val row1 = LinearLayout(this)
        btnScan = btn("SCAN") { startScan() }
        row1.addView(btn("PASTE") { paste() })
        row1.addView(btnScan)
        root.addView(row1)

        status = TextView(this).apply {
            text = "PASTE A LINK, HIT SCAN."
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)

        list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        list.setOnItemClickListener { _, _, _, _ -> refreshBtn() }

        val row2 = LinearLayout(this)
        row2.addView(btn("ALL / NONE") { toggleAll() })
        btnDl = btn("DOWNLOAD (0)") { download() }
        btnDl.isEnabled = false
        row2.addView(btnDl)
        root.addView(row2)

        setContentView(root)

        scanner = PageScanner(this, root, { s -> status.text = s }, { r -> showResults(r) })
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val t = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val link = Regex("https?://\\S+").find(t)?.value ?: t
            urlBox.setText(link)
            startScan()
        }
    }

    private fun paste() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        if (t.isNotBlank()) {
            urlBox.setText(t.trim())
            startScan()
        }
    }

    private fun startScan() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(urlBox.windowToken, 0)
        btnScan.isEnabled = false
        btnDl.isEnabled = false
        list.adapter = null
        items = emptyList()
        scanner.scan(urlBox.text.toString())
    }

    private fun showResults(r: List<Item>) {
        items = r
        btnScan.isEnabled = true
        list.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_multiple_choice,
            r.map { "${it.type} • ${fmt(it.size)}\n${it.name}" }
        )
        val onlyImages = r.all { it.type == "IMAGE" }
        r.forEachIndexed { i, item -> list.setItemChecked(i, onlyImages || item.type != "IMAGE") }
        status.text = if (r.isEmpty()) "NOTHING FOUND. SITE MAY NEED LOGIN OR USE DRM."
        else "FOUND ${r.size} FILES. PICK AND DOWNLOAD."
        refreshBtn()
    }

    private fun toggleAll() {
        val all = list.checkedItemCount == items.size
        for (i in items.indices) list.setItemChecked(i, !all)
        refreshBtn()
    }

    private fun refreshBtn() {
        val n = list.checkedItemCount
        btnDl.text = "DOWNLOAD ($n)"
        btnDl.isEnabled = n > 0
    }

    private fun download() {
        val sp = list.checkedItemPositions
        val chosen = ArrayList<Item>()
        for (i in 0 until sp.size()) {
            if (sp.valueAt(i) && sp.keyAt(i) < items.size) chosen.add(items[sp.keyAt(i)])
        }
        val (ok, skipped) = Downloader.start(this, chosen, scanner.pageUrl, scanner.ua, scanner.pageTitle)
        val msg = "STARTED $ok" +
            (if (skipped > 0) " • SKIPPED $skipped (STREAM/ERROR)" else "") +
            " → Downloads/PageGrab"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
