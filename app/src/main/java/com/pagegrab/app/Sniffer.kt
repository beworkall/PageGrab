package com.pagegrab.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors

data class Item(
    val url: String,
    var type: String,
    var size: Long = -1,
    var mime: String = "",
    var lastMod: String = "",
    var name: String = "",
    var ytArgs: List<String>? = null // non-null = download through the yt-dlp engine
)

data class Probe(val mime: String, val size: Long, val lastMod: String, val disp: String, val finalUrl: String)

private val MIN_IMG: Long get() = (if (Prefs.minImageKb > 0) Prefs.minImageKb else 10) * 1024L

object Kinds {
    private val groups = mapOf(
        "VIDEO" to "mp4 m4v webm mkv mov avi flv 3gp",
        "AUDIO" to "mp3 m4a aac ogg opus wav flac weba",
        "IMAGE" to "jpg jpeg png gif webp avif bmp heic",
        "DOC" to "pdf doc docx xls xlsx ppt pptx txt epub csv",
        "ARCHIVE" to "zip rar 7z tar gz apk",
        "STREAM" to "m3u8 mpd"
    )
    private val byExt: Map<String, String> =
        groups.flatMap { (k, v) -> v.split(" ").map { it to k } }.toMap()

    fun fromUrl(url: String): String? {
        val path = try { URI(url).path ?: "" } catch (e: Exception) { url.substringBefore('?') }
        return byExt[path.substringAfterLast('.', "").lowercase()]
    }

    fun fromMime(m0: String): String? {
        val m = m0.lowercase().substringBefore(';').trim()
        return when {
            m.contains("mpegurl") || m.contains("dash+xml") -> "STREAM"
            m.startsWith("video/") -> "VIDEO"
            m.startsWith("audio/") -> "AUDIO"
            m.startsWith("image/") -> "IMAGE"
            m == "application/pdf" || m.contains("msword") || m.contains("officedocument") ||
                m.contains("ms-excel") || m.contains("ms-powerpoint") || m == "text/plain" ||
                m == "text/csv" || m == "application/epub+zip" -> "DOC"
            m.contains("zip") || m.contains("rar") || m.contains("7z") || m.contains("gzip") ||
                m.contains("x-tar") || m.contains("android.package-archive") -> "ARCHIVE"
            m == "application/octet-stream" || m == "binary/octet-stream" -> "FILE"
            else -> null
        }
    }
}

/** HEAD request (falls back to a 1-byte ranged GET) to learn type, size, name, last-modified. */
fun probe(url: String, referer: String?, ua: String): Probe? {
    fun open(method: String, range: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", ua)
        if (referer != null) c.setRequestProperty("Referer", referer)
        CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
        if (range) c.setRequestProperty("Range", "bytes=0-0")
        return c
    }
    return try {
        var c = open("HEAD", false)
        var code = c.responseCode
        var viaRange = false
        if (code >= 400) {
            c.disconnect()
            c = open("GET", true)
            code = c.responseCode
            viaRange = true
        }
        if (code >= 400) {
            c.disconnect()
            null
        } else {
            var size = c.contentLengthLong
            if (viaRange) {
                val total = c.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
                if (total != null) size = total
            }
            val p = Probe(
                c.contentType ?: "", size,
                c.getHeaderField("Last-Modified") ?: "",
                c.getHeaderField("Content-Disposition") ?: "",
                c.url.toString()
            )
            c.disconnect()
            p
        }
    } catch (e: Exception) {
        null
    }
}

fun nameFor(p: Probe?, url: String, mime: String): String {
    var n = ""
    val d = p?.disp ?: ""
    if (d.isNotEmpty()) {
        val star = Regex("filename\\*=(?:UTF-8'')?([^;]+)", RegexOption.IGNORE_CASE).find(d)?.groupValues?.get(1)
        val plain = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(d)?.groupValues?.get(1)
        n = when {
            star != null -> try { URLDecoder.decode(star.trim().trim('"'), "UTF-8") } catch (e: Exception) { star }
            plain != null -> plain.trim()
            else -> ""
        }
    }
    if (n.isBlank()) {
        val path = try { URI(url).path ?: "" } catch (e: Exception) { url.substringBefore('?') }
        val last = path.substringAfterLast('/')
        n = try { URLDecoder.decode(last, "UTF-8") } catch (e: Exception) { last }
    }
    var s = n.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    if (s.isBlank()) s = "file"
    if (!s.contains('.')) {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.substringBefore(';').trim().lowercase())
        if (ext != null) s += ".$ext"
    }
    return s.take(120)
}

private const val JS = """
(function(){
var F=[],N=[];
function ab(u){try{return new URL(u,document.baseURI).href}catch(e){return null}}
function addF(u){u=u&&ab(u);if(u)F.push(u)}
function addN(u){u=u&&ab(u);if(u)N.push(u)}
document.querySelectorAll('video,audio,source,embed,object,track').forEach(function(e){addF(e.currentSrc);addF(e.getAttribute('src'));addF(e.getAttribute('data'))});
document.querySelectorAll('img').forEach(function(e){
 addN(e.currentSrc);addN(e.getAttribute('src'));
 ['data-src','data-original','data-lazy-src','data-full','data-large','data-zoom-src'].forEach(function(a){addN(e.getAttribute(a))});
 var ss=e.getAttribute('srcset')||e.getAttribute('data-srcset');
 if(ss){var bw=-1,bu=null;ss.split(',').forEach(function(p){var t=p.trim().split(/\s+/);var w=parseFloat(t[1])||1;if(w>bw){bw=w;bu=t[0]}});addN(bu)}
});
document.querySelectorAll('a[href]').forEach(function(a){addN(a.href)});
document.querySelectorAll('meta[property="og:video"],meta[property="og:video:url"],meta[property="og:video:secure_url"],meta[property="og:audio"],meta[name="twitter:player:stream"]').forEach(function(m){addF(m.content)});
document.querySelectorAll('meta[property="og:image"],meta[name="twitter:image"]').forEach(function(m){addN(m.content)});
document.querySelectorAll('script[type="application/ld+json"]').forEach(function(s){var re=/"(?:contentUrl|embedUrl)"\s*:\s*"([^"]+)"/g,m;while((m=re.exec(s.textContent))){addF(m[1].replace(/\\\//g,'/'))}});
return JSON.stringify({f:F,n:N});
})()
"""

class PageScanner(
    private val act: Activity,
    private val host: ViewGroup,
    private val status: (String) -> Unit,
    private val done: (List<Item>) -> Unit
) {
    private val pool = Executors.newFixedThreadPool(2)
    private val probes = Executors.newFixedThreadPool(8)
    private val ui = Handler(Looper.getMainLooper())

    var pageUrl = ""
    var pageTitle = ""
    var ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    fun scan(input: String) {
        var u = input.trim()
        if (u.isEmpty()) {
            status("PASTE A LINK FIRST")
            done(emptyList())
            return
        }
        if (!u.startsWith("http")) u = "https://$u"
        pageUrl = u
        pageTitle = ""
        status("CHECKING LINK...")
        val agent = ua
        pool.execute {
            val p = probe(u, null, agent)
            val mime = p?.mime?.lowercase() ?: ""
            val isPage = p == null || mime.isEmpty() || mime.contains("html")
            if (!isPage) {
                val t = Kinds.fromMime(mime) ?: Kinds.fromUrl(u) ?: "FILE"
                val item = Item(p!!.finalUrl, t, p.size, mime, p.lastMod, nameFor(p, u, mime))
                ui.post { done(listOf(item)) }
            } else {
                ui.post { status(if (Engine.isReady) "ASKING ENGINE..." else "STARTING ENGINE (FIRST RUN ~15s)...") }
                val info = Engine.info(act.applicationContext, u)
                if (info != null) {
                    ui.post {
                        pageTitle = info.title
                        done(Engine.items(u, info))
                    }
                } else {
                    ui.post { loadPage(u) }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadPage(u: String) {
        status("LOADING PAGE...")
        val net = LinkedHashSet<String>()
        val wv = WebView(act)
        host.addView(wv, ViewGroup.LayoutParams(1, 1))
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        ua = s.userAgentString
        var started = false
        var finished = false

        fun extract() {
            if (finished) return
            finished = true
            wv.evaluateJavascript(JS) { r ->
                pageTitle = wv.title ?: ""
                val forced = ArrayList<String>()
                val normal = ArrayList<String>()
                try {
                    val raw = JSONTokener(r).nextValue() as? String ?: "{}"
                    val o = JSONObject(raw)
                    val f = o.getJSONArray("f")
                    for (i in 0 until f.length()) forced.add(f.getString(i))
                    val n = o.getJSONArray("n")
                    for (i in 0 until n.length()) normal.add(n.getString(i))
                } catch (e: Exception) { /* use network hits only */ }
                synchronized(net) { normal.addAll(net) }
                host.removeView(wv)
                wv.destroy()
                resolve(forced, normal)
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val link = request.url.toString()
                if (Kinds.fromUrl(link) != null) synchronized(net) { net.add(link) }
                return null
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (started) return
                started = true
                status("SCANNING PAGE...")
                view.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight)", null)
                ui.postDelayed({ extract() }, 3000)
            }
        }
        // safety net: page never finishes loading
        ui.postDelayed({ if (!started) { started = true; extract() } }, 25000)
        wv.loadUrl(u)
    }

    private fun resolve(forced: List<String>, normal: List<String>) {
        status("CHECKING FILES...")
        val page = pageUrl
        val agent = ua
        pool.execute {
            val cand = LinkedHashSet<String>()
            for (u in forced) if (u.startsWith("http")) cand.add(u)
            for (u in normal) if (u.startsWith("http") && Kinds.fromUrl(u) != null) {
                if (!Prefs.includeImages && Kinds.fromUrl(u) == "IMAGE") continue
                cand.add(u)
            }
            val tasks = cand.take(250).map { link -> Callable<Item?> { makeItem(link, page, agent) } }
            val got = probes.invokeAll(tasks).mapNotNull { f -> try { f.get() } catch (e: Exception) { null } }
            val order = listOf("VIDEO", "AUDIO", "STREAM", "DOC", "ARCHIVE", "FILE", "IMAGE")
            val seen = HashSet<String>()
            val out = got.filter { seen.add(it.url) }
                .sortedWith(compareBy<Item>({ order.indexOf(it.type) }, { -it.size }))
            ui.post { done(out) }
        }
    }

    private fun makeItem(url: String, page: String, agent: String): Item? {
        val p = probe(url, page, agent)
        val mime = p?.mime ?: ""
        if (mime.lowercase().contains("html")) return null
        val extType = Kinds.fromUrl(url)
        val mimeType = if (p != null) Kinds.fromMime(mime) else null
        val type = (if (mimeType == null || mimeType == "FILE") (extType ?: mimeType) else mimeType) ?: return null
        if (type == "IMAGE" && p != null && p.size in 0 until MIN_IMG) return null
        return Item(p?.finalUrl ?: url, type, p?.size ?: -1, mime, p?.lastMod ?: "", nameFor(p, url, mime))
    }
}
