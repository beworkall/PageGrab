package com.pagegrab.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val F = "pagegrab_prefs"
    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences(F, Context.MODE_PRIVATE)
    }

    // Downloads
    var defaultQuality: String
        get() = p.getString("quality", "ASK") ?: "ASK"
        set(v) = p.edit().putString("quality", v).apply()
    var audioFormat: String
        get() = p.getString("audio_fmt", "M4A") ?: "M4A"
        set(v) = p.edit().putString("audio_fmt", v).apply()
    var maxParallel: Int
        get() = p.getInt("max_parallel", 2)
        set(v) = p.edit().putInt("max_parallel", v).apply()
    var wifiOnly: Boolean
        get() = p.getBoolean("wifi_only", false)
        set(v) = p.edit().putBoolean("wifi_only", v).apply()
    var retryCount: Int
        get() = p.getInt("retry_count", 2)
        set(v) = p.edit().putInt("retry_count", v).apply()

    // Engine
    var autoUpdateEngine: Boolean
        get() = p.getBoolean("auto_update_engine", true)
        set(v) = p.edit().putBoolean("auto_update_engine", v).apply()
    var extraYtArgs: String
        get() = p.getString("extra_yt_args", "") ?: ""
        set(v) = p.edit().putString("extra_yt_args", v).apply()

    // Media
    var embedThumb: Boolean
        get() = p.getBoolean("embed_thumb", true)
        set(v) = p.edit().putBoolean("embed_thumb", v).apply()
    var embedSubs: Boolean
        get() = p.getBoolean("embed_subs", false)
        set(v) = p.edit().putBoolean("embed_subs", v).apply()
    var writeInfoJson: Boolean
        get() = p.getBoolean("write_info_json", true)
        set(v) = p.edit().putBoolean("write_info_json", v).apply()
    var mergeFormat: String
        get() = p.getString("merge_fmt", "mp4") ?: "mp4"
        set(v) = p.edit().putString("merge_fmt", v).apply()

    // Proxy
    var proxyMode: String // OFF, SYSTEM, CUSTOM
        get() = p.getString("proxy_mode", "OFF") ?: "OFF"
        set(v) = p.edit().putString("proxy_mode", v).apply()
    var proxyType: String // http, socks5, socks4
        get() = p.getString("proxy_type", "http") ?: "http"
        set(v) = p.edit().putString("proxy_type", v).apply()
    var proxyHost: String
        get() = p.getString("proxy_host", "") ?: ""
        set(v) = p.edit().putString("proxy_host", v).apply()
    var proxyPort: String
        get() = p.getString("proxy_port", "") ?: ""
        set(v) = p.edit().putString("proxy_port", v).apply()
    var proxyUser: String
        get() = p.getString("proxy_user", "") ?: ""
        set(v) = p.edit().putString("proxy_user", v).apply()
    var proxyPass: String
        get() = p.getString("proxy_pass", "") ?: ""
        set(v) = p.edit().putString("proxy_pass", v).apply()

    fun proxyUrl(): String? {
        if (proxyMode != "CUSTOM" || proxyHost.isBlank() || proxyPort.isBlank()) return null
        val auth = if (proxyUser.isNotBlank()) "$proxyUser:$proxyPass@" else ""
        return "$proxyType://$auth$proxyHost:$proxyPort"
    }

    // Temp mail
    var tempMailEnabled: Boolean
        get() = p.getBoolean("temp_mail_enabled", false)
        set(v) = p.edit().putBoolean("temp_mail_enabled", v).apply()

    // Notifications / look
    var theme: String // SYSTEM, DARK, LIGHT
        get() = p.getString("theme", "SYSTEM") ?: "SYSTEM"
        set(v) = p.edit().putString("theme", v).apply()
    var progressNotif: Boolean
        get() = p.getBoolean("progress_notif", true)
        set(v) = p.edit().putBoolean("progress_notif", v).apply()

    // Sniffer
    var includeImages: Boolean
        get() = p.getBoolean("include_images", true)
        set(v) = p.edit().putBoolean("include_images", v).apply()
    var minImageKb: Int
        get() = p.getInt("min_image_kb", 10)
        set(v) = p.edit().putInt("min_image_kb", v).apply()
}
