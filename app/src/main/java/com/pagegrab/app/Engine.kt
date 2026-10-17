package com.pagegrab.app

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Modifier

data class Info(
    val title: String,
    val uploader: String,
    val duration: Long,
    val thumb: String,
    val maxH: Int,
    val hasVideo: Boolean,
    val hasAudio: Boolean
)

/** Wrapper around the bundled yt-dlp + ffmpeg. */
object Engine {

    @Volatile
    var isReady = false
        private set

    @Synchronized
    fun ensureInit(app: Context): Boolean {
        if (isReady) return true
        return try {
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
            isReady = true
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun isNone(s: String) = s.isEmpty() || s == "none" || s == "null"

    private fun str(j: JSONObject, key: String): String {
        val v = j.optString(key, "")
        return if (v == "null") "" else v
    }

    /** Asks yt-dlp what is on this URL (nothing is downloaded). Returns null if yt-dlp can't handle it. */
    fun info(ctx: Context, url: String): Info? {
        val app = ctx.applicationContext
        if (!ensureInit(app)) return null
        return try {
            val req = YoutubeDLRequest(url)
            req.addOption("--dump-single-json")
            req.addOption("--no-playlist")
            req.addOption("--no-warnings")
            req.addOption("--socket-timeout", "15")
            Prefs.proxyUrl()?.let { req.addOption("--proxy", it) }
            val out: String = YoutubeDL.getInstance().execute(req).out ?: return null
            val start = out.indexOf('{')
            if (start < 0) return null
            val j = JSONObject(out.substring(start))

            var maxH = 0
            var hasV = false
            var hasA = false
            val fs = j.optJSONArray("formats")
            if (fs != null) {
                for (i in 0 until fs.length()) {
                    val f = fs.optJSONObject(i) ?: continue
                    val vc = str(f, "vcodec")
                    val ac = str(f, "acodec")
                    val h = f.optInt("height", 0)
                    if (!isNone(vc)) {
                        hasV = true
                        if (h > maxH) maxH = h
                    }
                    if (!isNone(ac)) hasA = true
                }
            } else if (str(j, "url").isNotEmpty()) {
                hasV = !isNone(str(j, "vcodec"))
                hasA = !isNone(str(j, "acodec"))
                maxH = j.optInt("height", 0)
            }
            if (!hasV && !hasA) return null

            Info(
                str(j, "title"), str(j, "uploader"), j.optLong("duration", 0),
                str(j, "thumbnail"), maxH, hasV, hasA
            )
        } catch (e: Throwable) {
            null
        }
    }

    /** Turns yt-dlp info into pickable download options. */
    fun items(url: String, i: Info): List<Item> {
        val out = ArrayList<Item>()
        if (i.hasVideo) {
            val best = "BEST QUALITY" + (if (i.maxH > 0) " (${i.maxH}p)" else "") + " MP4"
            out.add(
                Item(
                    url, "VIDEO", -1, "", "", best,
                    listOf("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b", "--merge-output-format", "mp4")
                )
            )
            for (h in listOf(1080, 720, 480, 360)) {
                if (i.maxH == 0 || h < i.maxH) {
                    val f = "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/b[height<=$h][ext=mp4]/bv*[height<=$h]+ba/b[height<=$h]"
                    out.add(Item(url, "VIDEO", -1, "", "", "${h}p MP4", listOf("-f", f, "--merge-output-format", "mp4")))
                }
            }
        }
        if (i.hasAudio) {
            out.add(Item(url, "AUDIO", -1, "", "", "AUDIO ONLY M4A", listOf("-f", "ba[ext=m4a]/ba", "-x", "--audio-format", "m4a")))
            out.add(Item(url, "AUDIO", -1, "", "", "AUDIO ONLY MP3", listOf("-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "0")))
        }
        if (i.thumb.startsWith("http")) {
            out.add(Item(i.thumb, "IMAGE", -1, "", "", nameFor(null, i.thumb, "image/jpeg")))
        }
        return out
    }

    /**
     * Downloads one engine item. yt-dlp writes into app-private storage, then every result file
     * (media + .info.json with the original metadata) is copied to Downloads/PageGrab.
     */
    fun download(ctx: Context, item: Item, onProgress: (Int, Long) -> Unit): Pair<Boolean, String> {
        val app = ctx.applicationContext
        if (!ensureInit(app)) return Pair(false, "ENGINE FAILED TO START")
        val base = app.getExternalFilesDir(null) ?: app.filesDir
        val dir = File(base, "jobs/${System.currentTimeMillis()}")
        dir.mkdirs()
        return try {
            val req = YoutubeDLRequest(item.url)
            req.addOption("--no-playlist")
            req.addOption("-o", dir.absolutePath + "/%(title)s.%(ext)s")
            req.addOption("--trim-filenames", "120")
            req.addOption("--windows-filenames")
            req.addOption("--no-mtime")
            req.addOption("--embed-metadata")
            req.addOption("--concurrent-fragments", "4")
            if (Prefs.writeInfoJson) req.addOption("--write-info-json")
            if (Prefs.embedThumb) req.addOption("--embed-thumbnail")
            if (Prefs.embedSubs) {
                req.addOption("--embed-subs")
                req.addOption("--sub-langs", "all")
            }
            Prefs.proxyUrl()?.let { req.addOption("--proxy", it) }
            if (Prefs.extraYtArgs.isNotBlank()) {
                Prefs.extraYtArgs.trim().split(Regex("\\s+")).forEach { req.addOption(it) }
            }

            val a = item.ytArgs ?: emptyList()
            var i = 0
            while (i < a.size) {
                val k = a[i]
                if (k.startsWith("-") && i + 1 < a.size && !a[i + 1].startsWith("-")) {
                    req.addOption(k, a[i + 1])
                    i += 2
                } else {
                    req.addOption(k)
                    i++
                }
            }

            YoutubeDL.getInstance().execute(req, "pg" + System.nanoTime()) { p, eta, _ ->
                if (p >= 0f) onProgress(p.toInt(), eta)
            }

            var saved = 0
            dir.listFiles()?.forEach { f ->
                val n = f.name
                if (!n.endsWith(".part") && !n.endsWith(".ytdl") && !n.endsWith(".temp")) {
                    if (saveToDownloads(app, f)) saved++
                }
            }
            if (saved > 0) Pair(true, "SAVED $saved FILE(S) IN Downloads/PageGrab")
            else Pair(false, "NOTHING WAS SAVED")
        } catch (e: Throwable) {
            Pair(false, "ERROR: " + (e.message ?: "unknown").take(140))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun saveToDownloads(ctx: Context, f: File): Boolean {
        return try {
            val ext = f.extension.lowercase()
            val mime = if (ext == "json") "application/json"
            else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, f.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/PageGrab")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { os -> f.inputStream().use { it.copyTo(os) } }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Updates yt-dlp itself (YouTube breaks old versions every few weeks).
     * Uses reflection for the channel argument so a library version change can't break the build.
     */
    fun update(ctx: Context): String {
        val app = ctx.applicationContext
        if (!ensureInit(app)) return "ENGINE FAILED TO START"
        return try {
            val yd = YoutubeDL.getInstance()
            val m = yd.javaClass.methods.firstOrNull {
                it.name == "updateYoutubeDL" && it.parameterTypes.isNotEmpty() &&
                    Context::class.java.isAssignableFrom(it.parameterTypes[0])
            } ?: return "UPDATE NOT AVAILABLE IN THIS LIB VERSION"

            val res: Any? = if (m.parameterTypes.size == 1) {
                m.invoke(yd, app)
            } else {
                val pt = m.parameterTypes[1]
                var channel: Any? = pt.fields.firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.name.contains("STABLE", ignoreCase = true)
                }?.get(null)
                if (channel == null) {
                    channel = pt.classes.firstOrNull { it.simpleName.contains("STABLE", ignoreCase = true) }
                        ?.getField("INSTANCE")?.get(null)
                }
                m.invoke(yd, app, channel)
            }
            "ENGINE UPDATE: " + (res?.toString() ?: "DONE")
        } catch (e: Throwable) {
            "UPDATE FAILED: " + (e.cause?.message ?: e.message ?: "?").take(100)
        }
    }
}
