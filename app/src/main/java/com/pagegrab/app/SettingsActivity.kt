package com.pagegrab.app

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.*
import java.io.File

class SettingsActivity : Activity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun heading(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            setText(text)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(4))
        })
    }

    private fun switchRow(parent: LinearLayout, label: String, get: () -> Boolean, set: (Boolean) -> Unit): Switch {
        val row = LinearLayout(this)
        row.addView(TextView(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val sw = Switch(this).apply { isChecked = get() }
        sw.setOnCheckedChangeListener { _, v -> set(v) }
        row.addView(sw)
        parent.addView(row)
        return sw
    }

    private fun editRow(parent: LinearLayout, label: String, get: () -> String, set: (String) -> Unit, numeric: Boolean = false) {
        parent.addView(TextView(this).apply { text = label; setPadding(0, dp(6), 0, 0) })
        val e = EditText(this).apply {
            setText(get())
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        }
        e.setOnFocusChangeListener { _, has -> if (!has) set(e.text.toString()) }
        parent.addView(e)
    }

    private fun spinnerRow(parent: LinearLayout, label: String, options: List<String>, get: () -> String, set: (String) -> Unit) {
        parent.addView(TextView(this).apply { text = label; setPadding(0, dp(6), 0, 0) })
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        val idx = options.indexOf(get()).let { if (it < 0) 0 else it }
        sp.setSelection(idx)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) { set(options[pos]) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        parent.addView(sp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)

        val scroll = ScrollView(this)
        UI.applyWindowBg(scroll)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(32))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "SETTINGS"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })

        // ---------------- DOWNLOADS ----------------
        heading(root, "DOWNLOADS")
        spinnerRow(root, "Default quality", listOf("ASK", "BEST", "1080", "720", "480", "AUDIO"), { Prefs.defaultQuality }, { Prefs.defaultQuality = it })
        spinnerRow(root, "Default audio format", listOf("M4A", "MP3"), { Prefs.audioFormat }, { Prefs.audioFormat = it })
        editRow(root, "Max parallel downloads", { Prefs.maxParallel.toString() }, { Prefs.maxParallel = it.toIntOrNull()?.coerceIn(1, 5) ?: 2 }, true)
        editRow(root, "Retry attempts on failure", { Prefs.retryCount.toString() }, { Prefs.retryCount = it.toIntOrNull()?.coerceIn(0, 5) ?: 2 }, true)
        switchRow(root, "Wi-Fi only", { Prefs.wifiOnly }, { Prefs.wifiOnly = it })

        // ---------------- ENGINE ----------------
        heading(root, "ENGINE (yt-dlp)")
        switchRow(root, "Auto-update engine on launch", { Prefs.autoUpdateEngine }, { Prefs.autoUpdateEngine = it })
        val verRow = TextView(this).apply { text = "Installed: checking..." }
        root.addView(verRow)
        Thread {
            val v = try { com.yausername.youtubedl_android.YoutubeDL.getInstance().version(this).toString() } catch (e: Exception) { "unknown" }
            runOnUiThread { verRow.text = "Installed engine: $v" }
        }.start()
        root.addView(Button(this).apply {
            text = "UPDATE ENGINE NOW"
            setOnClickListener {
                Toast.makeText(this@SettingsActivity, "UPDATING...", Toast.LENGTH_SHORT).show()
                Thread {
                    val r = Engine.update(this@SettingsActivity)
                    runOnUiThread { Toast.makeText(this@SettingsActivity, r, Toast.LENGTH_LONG).show() }
                }.start()
            }
        })
        editRow(root, "Extra yt-dlp arguments (advanced)", { Prefs.extraYtArgs }, { Prefs.extraYtArgs = it })

        // ---------------- MEDIA ----------------
        heading(root, "MEDIA")
        switchRow(root, "Embed thumbnail", { Prefs.embedThumb }, { Prefs.embedThumb = it })
        switchRow(root, "Embed subtitles when available", { Prefs.embedSubs }, { Prefs.embedSubs = it })
        switchRow(root, "Save original info.json", { Prefs.writeInfoJson }, { Prefs.writeInfoJson = it })
        spinnerRow(root, "Merge format", listOf("mp4", "mkv"), { Prefs.mergeFormat }, { Prefs.mergeFormat = it })

        // ---------------- PROXY ----------------
        heading(root, "PROXY")
        spinnerRow(root, "Mode", listOf("OFF", "SYSTEM", "CUSTOM"), { Prefs.proxyMode }, { Prefs.proxyMode = it })
        spinnerRow(root, "Type", listOf("http", "socks5", "socks4"), { Prefs.proxyType }, { Prefs.proxyType = it })
        editRow(root, "Host", { Prefs.proxyHost }, { Prefs.proxyHost = it })
        editRow(root, "Port", { Prefs.proxyPort }, { Prefs.proxyPort = it }, true)
        editRow(root, "Username (optional)", { Prefs.proxyUser }, { Prefs.proxyUser = it })
        editRow(root, "Password (optional)", { Prefs.proxyPass }, { Prefs.proxyPass = it })
        root.addView(TextView(this).apply {
            text = "Applies to the engine, the page sniffer, and file downloads (the last one switches to an internal downloader when a proxy is set, since Android's system downloader can't use one)."
            setPadding(0, dp(4), 0, 0)
            alpha = 0.7f
        })
        root.addView(Button(this).apply {
            text = "TEST PROXY"
            setOnClickListener {
                Toast.makeText(this@SettingsActivity, "TESTING...", Toast.LENGTH_SHORT).show()
                Thread {
                    val ok = testProxy()
                    runOnUiThread {
                        Toast.makeText(this@SettingsActivity, if (ok) "PROXY OK" else "PROXY FAILED", Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
        })

        // ---------------- TEMP MAIL ----------------
        heading(root, "TEMP MAIL")
        switchRow(root, "Use temp mail for signups (opt-in)", { Prefs.tempMailEnabled }, { Prefs.tempMailEnabled = it })
        root.addView(TextView(this).apply {
            text = "Generates a disposable address you can paste into a signup form yourself. You still type your password and tap Sign Up/Verify — nothing is auto-submitted."
            setPadding(0, dp(4), 0, 0)
            alpha = 0.7f
        })

        // ---------------- NOTIFICATIONS AND LOOK ----------------
        heading(root, "NOTIFICATIONS AND LOOK")
        switchRow(root, "Show download progress notification", { Prefs.progressNotif }, { Prefs.progressNotif = it })
        spinnerRow(root, "Theme", listOf("SYSTEM", "DARK", "LIGHT"), { Prefs.theme }, { Prefs.theme = it })
        root.addView(TextView(this).apply {
            text = "Language: set from Android Settings > System > Languages > App languages > PageGrab (Android 13+), or your device language."
            setPadding(0, dp(4), 0, 0)
            alpha = 0.7f
        })

        // ---------------- SNIFFER ----------------
        heading(root, "PAGE SNIFFER")
        switchRow(root, "Include images", { Prefs.includeImages }, { Prefs.includeImages = it })
        editRow(root, "Minimum image size to list (KB)", { Prefs.minImageKb.toString() }, { Prefs.minImageKb = it.toIntOrNull() ?: 10 }, true)

        // ---------------- STORAGE AND PRIVACY ----------------
        heading(root, "STORAGE AND PRIVACY")
        root.addView(Button(this).apply {
            text = "CLEAR TEMP CACHE"
            setOnClickListener {
                try {
                    File(cacheDir, "").deleteRecursively()
                    getExternalFilesDir(null)?.let { File(it, "jobs").deleteRecursively() }
                } catch (e: Exception) { }
                Toast.makeText(this@SettingsActivity, "CACHE CLEARED", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(TextView(this).apply {
            text = "No analytics, ever."
            setPadding(0, dp(4), 0, 0)
            alpha = 0.7f
        })

        // ---------------- ABOUT ----------------
        heading(root, "ABOUT")
        val v = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        root.addView(TextView(this).apply { text = "PageGrab v$v" })
        root.addView(TextView(this).apply { text = "License: GPL-3.0 (bundles yt-dlp + ffmpeg)" })
        root.addView(TextView(this).apply { text = "Credits: yt-dlp, ffmpeg, youtubedl-android, mail.tm" })
        root.addView(TextView(this).apply {
            text = "Only download content you have the right to access and save."
            setPadding(0, dp(4), 0, dp(4))
            alpha = 0.7f
        })
        root.addView(Button(this).apply {
            text = "VIEW ON GITHUB"
            setOnClickListener {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/beworkall/PageGrab")))
            }
        })

        setContentView(scroll)
    }

    private fun testProxy(): Boolean {
        val url = Prefs.proxyUrl() ?: return false
        return try {
            val u = java.net.URI(url)
            val type = if (Prefs.proxyType == "http") java.net.Proxy.Type.HTTP else java.net.Proxy.Type.SOCKS
            val proxy = java.net.Proxy(type, java.net.InetSocketAddress(u.host, u.port))
            val conn = (java.net.URL("https://www.google.com").openConnection(proxy)) as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (e: Exception) { false }
    }
}
