package com.pagegrab.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.util.concurrent.Executors

/**
 * Manual in-app login. The user logs into the REAL site with their OWN credentials in a normal
 * WebView; PageGrab just keeps the resulting cookies so the yt-dlp engine can reuse that session
 * (private videos, members-only-but-free posts, age-gated content you're entitled to see).
 * Nothing here auto-fills passwords or auto-submits login/signup forms.
 */
class LoginActivity : Activity() {

    private lateinit var urlBox: EditText
    private lateinit var web: WebView
    private lateinit var tempPanel: LinearLayout
    private lateinit var tempStatus: TextView
    private lateinit var inboxList: ListView
    private val worker = Executors.newSingleThreadExecutor()
    private var tempAcct: TempMail.Account? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(12))
        }
        root.addView(TextView(this).apply {
            text = "LOG IN (YOUR OWN ACCOUNT)"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Log in normally below. PageGrab reuses the session cookies so the engine can fetch content you already have access to."
            setPadding(0, dp(4), 0, dp(8))
        })

        urlBox = EditText(this).apply {
            hint = "SITE LOGIN URL, e.g. https://example.com/login"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(urlBox)

        val row = LinearLayout(this)
        row.addView(Button(this).apply {
            text = "OPEN"
            setOnClickListener { web.loadUrl(fix(urlBox.text.toString())) }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup_WRAP, 1f)
        })
        row.addView(Button(this).apply {
            text = "DONE (SAVE SESSION)"
            setOnClickListener { CookieManager.getInstance().flush(); Toast.makeText(this@LoginActivity, "SESSION SAVED", Toast.LENGTH_SHORT).show() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup_WRAP, 1f)
        })
        root.addView(row)

        if (Prefs.tempMailEnabled) {
            tempPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            tempPanel.addView(TextView(this).apply {
                text = "TEMP MAIL"
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
            })
            val trow = LinearLayout(this)
            trow.addView(Button(this).apply {
                text = "FILL TEMP EMAIL"
                setOnClickListener { fillTempEmail() }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup_WRAP, 1f)
            })
            trow.addView(Button(this).apply {
                text = "CHECK INBOX"
                setOnClickListener { checkInbox() }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup_WRAP, 1f)
            })
            tempPanel.addView(trow)
            tempStatus = TextView(this).apply { text = "No temp address yet." }
            tempPanel.addView(tempStatus)
            inboxList = ListView(this)
            tempPanel.addView(inboxList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)))
            root.addView(tempPanel)
        }

        web = WebView(this)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        web.webChromeClient = object : WebChromeClient() {
            // Google/Facebook/etc "sign in" buttons open via window.open() — without this,
            // a plain WebView just eats that click and nothing happens.
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                val popup = WebView(this@LoginActivity)
                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                val dialog = android.app.Dialog(this@LoginActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                val box = LinearLayout(this@LoginActivity).apply { orientation = LinearLayout.VERTICAL }
                box.addView(Button(this@LoginActivity).apply {
                    text = "✕ CLOSE"
                    setOnClickListener { dialog.dismiss() }
                })
                box.addView(popup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                popup.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView, url: String) {
                        // most OAuth popups close themselves on success; this catches the rest
                        if (url.contains("close") || url == "about:blank") dialog.dismiss()
                    }
                }
                dialog.setContentView(box)
                dialog.setOnDismissListener { CookieManager.getInstance().flush() }
                dialog.show()
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private val ViewGroup_WRAP get() = android.view.ViewGroup.LayoutParams.WRAP_CONTENT

    private fun fix(u: String) = if (u.startsWith("http")) u else "https://$u"

    private fun fillTempEmail() {
        tempStatus.text = "GENERATING ADDRESS..."
        worker.execute {
            val acct = TempMail.createAccount()
            runOnUiThread {
                if (acct == null) {
                    tempStatus.text = "COULD NOT GENERATE AN ADDRESS (service may be blocked by this network/site)."
                    return@runOnUiThread
                }
                tempAcct = acct
                tempStatus.text = "ADDRESS: ${acct.address}\nTap an email field on the page below, then paste it in."
                // Copies the address to the clipboard — user pastes it into the field themselves.
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("email", acct.address))
                Toast.makeText(this, "ADDRESS COPIED: ${acct.address}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkInbox() {
        val acct = tempAcct ?: run {
            Toast.makeText(this, "GENERATE A TEMP EMAIL FIRST", Toast.LENGTH_SHORT).show()
            return
        }
        tempStatus.text = "CHECKING..."
        worker.execute {
            val msgs = TempMail.listMessages(acct.token)
            runOnUiThread {
                tempStatus.text = "ADDRESS: ${acct.address} • ${msgs.size} MESSAGE(S)"
                inboxList.adapter = ArrayAdapter(
                    this, android.R.layout.simple_list_item_1,
                    msgs.map { "${it.from}: ${it.subject}" }
                )
                inboxList.setOnItemClickListener { _, _, i, _ ->
                    worker.execute {
                        val body = TempMail.readMessage(acct.token, msgs[i].id)
                        runOnUiThread { showBody(msgs[i].subject, body) }
                    }
                }
            }
        }
    }

    private fun showBody(subject: String, body: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(subject)
            .setMessage(body.take(4000))
            .setPositiveButton("CLOSE", null)
            .show()
    }
}
