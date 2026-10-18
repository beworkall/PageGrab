package com.pagegrab.app

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private fun fmt(b: Long): String = when {
    b < 0 -> "SIZE ?"
    b < 1024 -> "$b B"
    b < 1048576 -> "${b / 1024} KB"
    else -> String.format("%.1f MB", b / 1048576.0)
}

private fun label(item: Item): String =
    if (item.ytArgs != null) "${item.type} • ENGINE\n${item.name}"
    else "${item.type} • ${fmt(item.size)}\n${item.name}"

class MainActivity : Activity() {

    private lateinit var urlBox: android.widget.EditText
    private lateinit var status: TextView
    private lateinit var list: ListView
    private lateinit var btnScan: android.widget.Button
    private lateinit var btnDl: android.widget.Button
    private lateinit var scanner: PageScanner
    private var items: List<Item> = emptyList()

    private fun dp(n: Int) = UI.dp(this, n)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(32), dp(14), dp(10))
        }
        UI.applyWindowBg(outer)

        // ---------------- header ----------------
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(UI.title(this, "PAGEGRAB").also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(UI.iconButton(this, "⚙") { startActivity(Intent(this, SettingsActivity::class.java)) })
        header.addView(UI.iconButton(this, "🕘") { startActivity(Intent(this, HistoryActivity::class.java)) })
        header.addView(UI.iconButton(this, "🌐") { startActivity(Intent(this, BrowserActivity::class.java)) })
        outer.addView(header)

        // ---------------- url card ----------------
        val urlCard = UI.card(this)
        urlBox = UI.styledEditText(this, "Paste any link").apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> startScan(); true }
        }
        urlCard.addView(urlBox)
        val row1 = LinearLayout(this).apply { setPadding(0, dp(10), 0, 0) }
        row1.addView(UI.secondaryButton(this, "PASTE") { paste() }.also {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp(8)
            it.layoutParams = lp
        })
        btnScan = UI.primaryButton(this, "SCAN") { startScan() }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row1.addView(btnScan)
        urlCard.addView(row1)
        outer.addView(urlCard)

        status = UI.label(this, "Paste a link, hit Scan.", dim = true).apply {
            setPadding(dp(4), dp(10), dp(4), dp(6))
        }
        outer.addView(status)

        // ---------------- results list ----------------
        list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE }
        outer.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        list.setOnItemClickListener { _, _, _, _ -> refreshBtn() }

        val row2 = LinearLayout(this).apply { setPadding(0, dp(8), 0, 0) }
        row2.addView(UI.secondaryButton(this, "ALL / NONE") { toggleAll() }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        btnDl = UI.primaryButton(this, "DOWNLOAD (0)") { download() }.also {
            it.isEnabled = false
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row2.addView(btnDl)
        outer.addView(row2)

        setContentView(outer)

        scanner = PageScanner(this, outer, { s -> status.text = s }, { r -> showResults(r) })
        handleIntent(intent)

        if (Prefs.autoUpdateEngine) Thread { Engine.update(this) }.start()
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
        list.adapter = CheckAdapter(this, r.map { label(it) })
        val engine = r.any { it.ytArgs != null }
        val onlyImages = r.all { it.type == "IMAGE" }
        r.forEachIndexed { i, item ->
            list.setItemChecked(i, if (engine) i == 0 else (onlyImages || item.type != "IMAGE"))
        }
        status.text = when {
            r.isEmpty() -> "Nothing found. Site may need login or use DRM."
            engine -> "Video: ${scanner.pageTitle.take(70)} — pick a quality, then Download."
            else -> "Found ${r.size} files. Pick and Download."
        }
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
        val engineJobs = chosen.filter { it.ytArgs != null }
        val plain = chosen.filter { it.ytArgs == null }

        if (plain.isNotEmpty()) {
            val (ok, skipped) = Downloader.start(this, plain, scanner.pageUrl, scanner.ua, scanner.pageTitle)
            val msg = "Started $ok" + (if (skipped > 0) " • skipped $skipped (stream/error)" else "") + " → Downloads/PageGrab"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
        if (engineJobs.isNotEmpty()) runEngine(engineJobs)
    }

    /** Queues the picked items and starts the background service — downloads now survive leaving the app. */
    private fun runEngine(jobs: List<Item>) {
        jobs.forEach { JobQueue.add(it, scanner.pageUrl, scanner.pageTitle, scanner.ua) }
        ContextCompat.startForegroundService(this, Intent(this, DownloadService::class.java))
        status.text = "Queued ${jobs.size} job(s). Running in background — check the notification, or History when done."
        Toast.makeText(this, "Queued ${jobs.size} download(s)", Toast.LENGTH_SHORT).show()
        refreshBtn()
    }
}
