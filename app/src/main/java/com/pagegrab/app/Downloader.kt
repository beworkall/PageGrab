package com.pagegrab.app

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Downloader {

    /** Returns (started, skipped). Uses Android's built-in DownloadManager: resumable, notifications, no extra libs.
     *  When a custom proxy is set, DownloadManager can't use it, so files are fetched with a small
     *  internal downloader instead (proxyDownload below). */
    fun start(ctx: Context, items: List<Item>, pageUrl: String, ua: String, title: String): Pair<Int, Int> {
        if (Prefs.proxyMode == "CUSTOM" && Prefs.proxyUrl() != null) {
            return proxyDownload(ctx, items, pageUrl, ua, title)
        }
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val used = HashSet<String>()
        val log = JSONArray()
        var ok = 0
        var skipped = 0

        for (item in items) {
            if (item.type == "STREAM") { skipped++; continue }

            var name = item.name
            if (!used.add(name.lowercase())) {
                val base = name.substringBeforeLast('.', name)
                val ext = name.substringAfterLast('.', "")
                var i = 2
                do {
                    name = if (ext.isEmpty()) "${base}_$i" else "${base}_$i.$ext"
                    i++
                } while (!used.add(name.lowercase()))
            }

            try {
                val r = DownloadManager.Request(Uri.parse(item.url))
                    .addRequestHeader("User-Agent", ua)
                    .addRequestHeader("Referer", pageUrl)
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PageGrab/$name")
                CookieManager.getInstance().getCookie(item.url)?.let { r.addRequestHeader("Cookie", it) }
                dm.enqueue(r)
                ok++
                log.put(
                    JSONObject()
                        .put("file", name)
                        .put("url", item.url)
                        .put("type", item.type)
                        .put("mime", item.mime)
                        .put("size_bytes", item.size)
                        .put("last_modified", item.lastMod)
                )
            } catch (e: Exception) {
                skipped++
            }
        }

        if (ok > 0) writeLog(ctx, pageUrl, title, log)
        return Pair(ok, skipped)
    }

    /** Fallback used only when a custom proxy is set: downloads via HttpURLConnection through that proxy. */
    private fun proxyDownload(ctx: Context, items: List<Item>, pageUrl: String, ua: String, title: String): Pair<Int, Int> {
        val proxyUrl = Prefs.proxyUrl() ?: return Pair(0, items.size)
        val uri = java.net.URI(proxyUrl)
        val type = if (Prefs.proxyType == "http") java.net.Proxy.Type.HTTP else java.net.Proxy.Type.SOCKS
        val proxy = java.net.Proxy(type, java.net.InetSocketAddress(uri.host, uri.port))
        val log = JSONArray()
        var ok = 0
        var skipped = 0
        for (item in items) {
            if (item.type == "STREAM") { skipped++; continue }
            try {
                val c = (java.net.URL(item.url).openConnection(proxy)) as java.net.HttpURLConnection
                c.setRequestProperty("User-Agent", ua)
                c.setRequestProperty("Referer", pageUrl)
                CookieManager.getInstance().getCookie(item.url)?.let { c.setRequestProperty("Cookie", it) }
                c.connectTimeout = 15000
                c.readTimeout = 20000
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, item.name)
                    put(MediaStore.Downloads.MIME_TYPE, item.mime.ifBlank { "application/octet-stream" })
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/PageGrab")
                }
                val outUri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: continue
                ctx.contentResolver.openOutputStream(outUri)?.use { os -> c.inputStream.use { it.copyTo(os) } }
                c.disconnect()
                ok++
                log.put(JSONObject().put("file", item.name).put("url", item.url).put("type", item.type))
            } catch (e: Exception) {
                skipped++
            }
        }
        if (ok > 0) writeLog(ctx, pageUrl, title, log)
        return Pair(ok, skipped)
    }

    /** Sidecar JSON next to the downloads: where each file came from + server-side metadata. */
    private fun writeLog(ctx: Context, pageUrl: String, title: String, log: JSONArray) {
        try {
            val now = Date()
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(now)
            val host = Uri.parse(pageUrl).host ?: "page"
            val json = JSONObject()
                .put("page_url", pageUrl)
                .put("page_title", title)
                .put("saved_at", iso)
                .put("files", log)
                .toString(2)
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "pagegrab_${host}_${now.time / 1000}.json")
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/PageGrab")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            // log is a bonus; never block downloads
        }
    }
}
