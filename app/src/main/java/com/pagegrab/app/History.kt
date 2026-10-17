package com.pagegrab.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryEntry(val title: String, val url: String, val type: String, val savedAt: String, val ok: Boolean)

object History {
    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "history.json")

    @Synchronized
    fun add(ctx: Context, title: String, url: String, type: String, ok: Boolean) {
        try {
            val f = file(ctx)
            val arr = if (f.exists()) JSONArray(f.readText()) else JSONArray()
            val e = JSONObject()
                .put("title", title.ifBlank { url })
                .put("url", url)
                .put("type", type)
                .put("ok", ok)
                .put("saved_at", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            arr.put(e)
            // keep last 300 entries so the file never grows unbounded
            val trimmed = JSONArray()
            val start = maxOf(0, arr.length() - 300)
            for (i in start until arr.length()) trimmed.put(arr.get(i))
            f.writeText(trimmed.toString())
        } catch (e: Exception) { /* history is a bonus feature, never block downloads */ }
    }

    fun all(ctx: Context): List<HistoryEntry> {
        return try {
            val f = file(ctx)
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText())
            val out = ArrayList<HistoryEntry>()
            for (i in arr.length() - 1 downTo 0) {
                val o = arr.getJSONObject(i)
                out.add(HistoryEntry(o.optString("title"), o.optString("url"), o.optString("type"), o.optString("saved_at"), o.optBoolean("ok", true)))
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    @Synchronized
    fun clear(ctx: Context) {
        try { file(ctx).delete() } catch (e: Exception) { }
    }
}
