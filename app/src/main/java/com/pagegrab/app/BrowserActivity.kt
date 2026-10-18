package com.pagegrab.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import java.util.concurrent.Executors

/**
 * Full in-app browser used for logging into sites with your OWN account, so the engine can reuse
 * the session cookie afterward. Real address bar + back/forward/refresh, optional Incognito (no
 * cookies/history kept), optional Desktop site, and an opt-in Temp Mail tab for signups.
 * Nothing here auto-fills passwords or auto-submits login/signup forms.
 */
class BrowserActivity : Activity() {

    private lateinit var addressBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var web: WebView
    private lateinit var incognitoBtn: TextView
    private lateinit var tempPanel: LinearLayout
    private lateinit var tempStatus: TextView
    private lateinit var inboxList: ListView
    private val worker = Executors.newSingleThreadExecutor()
    private var tempAcct: TempMail.Account? = null
    private var incognito = false
    private var desktopMode = false

    private fun dp(n: Int) = UI.dp(this, n)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(28), dp(10), dp(6))
        }
        UI.applyWindowBg(root)

        // ---------------- toolbar ----------------
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(UI.iconButton(this, "‹") { if (web.canGoBack()) web.goBack() })
        toolbar.addView(UI.iconButton(this, "›") { if (web.canGoForward()) web.goForward() })
        toolbar.addView(UI.iconButton(this, "↻") { web.reload() })
        addressBar = UI.styledEditText(this, "Search or enter a site").apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, _, _ -> go(text.toString()); true }
        }
        toolbar.addView(addressBar)
        root.addView(toolbar)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
        }
        root.addView(progress)

        // ---------------- action row ----------------
        val actions = LinearLayout(this)
        incognitoBtn = TextView(this)
        actions.addView(UI.secondaryButton(this, "🕶 INCOGNITO") { toggleIncognito() }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            incognitoBtn = it
        })
        actions.addView(UI.secondaryButton(this, "DESKTOP") { toggleDesktop() }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        actions.addView(UI.secondaryButton(this, "↗ OPEN OUTSIDE") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web.url))) } catch (e: Exception) { }
        }.also { it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        root.addView(actions)

        if (Prefs.tempMailEnabled) root.addView(buildTempMailPanel())

        web = WebView(this)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                progress.progress = p
                progress.visibility = if (p >= 100) android.view.View.INVISIBLE else android.view.View.VISIBLE
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                val popup = WebView(this@BrowserActivity)
                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                val dialog = android.app.Dialog(this@BrowserActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                val box = LinearLayout(this@BrowserActivity).apply { orientation = LinearLayout.VERTICAL }
                UI.applyWindowBg(box)
                box.addView(UI.secondaryButton(this@BrowserActivity, "✕ CLOSE") { dialog.dismiss() })
                box.addView(popup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                popup.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView, url: String) {
                        if (url.contains("close") || url == "about:blank") dialog.dismiss()
                    }
                }
                dialog.setContentView(box)
                dialog.setOnDismissListener { if (!incognito) CookieManager.getInstance().flush() }
                dialog.show()
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) { addressBar.setText(url) }
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        applyIncognitoState()
        maybeShowTempMailPrompt()
    }

    private fun go(input: String) {
        var u = input.trim()
        if (u.isEmpty()) return
        if (!u.contains(" ") && u.contains(".")) {
            if (!u.startsWith("http")) u = "https://$u"
        } else {
            u = "https://www.google.com/search?q=" + Uri.encode(u)
        }
        web.loadUrl(u)
    }

    // ---------------- Incognito ----------------
    private fun toggleIncognito() {
        incognito = !incognito
        if (incognito) {
            CookieManager.getInstance().removeAllCookies(null)
            web.clearHistory()
            web.clearCache(true)
        }
        applyIncognitoState()
        Toast.makeText(this, if (incognito) "INCOGNITO ON — nothing will be saved" else "INCOGNITO OFF", Toast.LENGTH_SHORT).show()
    }

    private fun applyIncognitoState() {
        CookieManager.getInstance().setAcceptCookie(!incognito)
        web.settings.domStorageEnabled = true
        web.settings.cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        incognitoBtn.setTextColor(Color.parseColor(if (incognito) UI.DANGER else UI.TEXT))
    }

    override fun onDestroy() {
        if (incognito) {
            CookieManager.getInstance().removeAllCookies(null)
            web.clearHistory()
            web.clearCache(true)
            web.clearFormData()
        }
        super.onDestroy()
    }

    private fun toggleDesktop() {
        desktopMode = !desktopMode
        web.settings.userAgentString = if (desktopMode)
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        else null
        web.reload()
        Toast.makeText(this, if (desktopMode) "DESKTOP SITE ON" else "DESKTOP SITE OFF", Toast.LENGTH_SHORT).show()
    }

    // ---------------- Temp mail (opt-in, first-run prompt) ----------------
    private fun maybeShowTempMailPrompt() {
        if (Prefs.tempMailPromptShown) return
        Prefs.tempMailPromptShown = true
        android.app.AlertDialog.Builder(this)
            .setTitle("Use temp mail?")
            .setMessage("For signups on this or other sites, PageGrab can generate a disposable email address to protect your real inbox from spam. You still type the password and tap Sign Up yourself — nothing is automatic. Turn it on?")
            .setPositiveButton("USE TEMP MAIL") { _, _ ->
                Prefs.tempMailEnabled = true
                recreate()
            }
            .setNegativeButton("NOT NOW", null)
            .show()
    }

    private fun buildTempMailPanel(): LinearLayout {
        tempPanel = UI.card(this)
        tempPanel.addView(UI.heading(this, "TEMP MAIL"))
        val row = LinearLayout(this)
        row.addView(UI.secondaryButton(this, "FILL TEMP EMAIL") { fillTempEmail() }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(UI.secondaryButton(this, "CHECK INBOX") { checkInbox() }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        tempPanel.addView(row)
        tempStatus = UI.label(this, "No temp address yet.", dim = true)
        tempPanel.addView(tempStatus)
        inboxList = ListView(this)
        tempPanel.addView(inboxList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)))
        return tempPanel
    }

    private fun fillTempEmail() {
        tempStatus.text = "GENERATING ADDRESS..."
        worker.execute {
            val acct = TempMail.createAccount()
            runOnUiThread {
                if (acct == null) {
                    tempStatus.text = "Could not generate an address (this network/site may block the temp-mail service)."
                    return@runOnUiThread
                }
                tempAcct = acct
                tempStatus.text = "Address: ${acct.address}\nTap the site's email field, then paste it in."
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("email", acct.address))
                Toast.makeText(this, "Address copied: ${acct.address}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkInbox() {
        val acct = tempAcct ?: run {
            Toast.makeText(this, "Generate a temp email first", Toast.LENGTH_SHORT).show()
            return
        }
        tempStatus.text = "Checking..."
        worker.execute {
            val msgs = TempMail.listMessages(acct.token)
            runOnUiThread {
                tempStatus.text = "${acct.address} • ${msgs.size} message(s)"
                inboxList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, msgs.map { "${it.from}: ${it.subject}" })
                inboxList.setOnItemClickListener { _, _, i, _ ->
                    worker.execute {
                        val body = TempMail.readMessage(acct.token, msgs[i].id)
                        runOnUiThread {
                            android.app.AlertDialog.Builder(this)
                                .setTitle(msgs[i].subject)
                                .setMessage(body.take(4000))
                                .setPositiveButton("CLOSE", null)
                                .show()
                        }
                    }
                }
            }
        }
    }
}
