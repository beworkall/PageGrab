package com.pagegrab.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin client for the mail.tm public temp-mail API.
 * Used only when the user opts in via Settings > Use temp mail for signups.
 * Nothing here auto-submits forms or auto-clicks verification links — the user still does that.
 */
object TempMail {
    private const val BASE = "https://api.mail.tm"

    data class Account(val address: String, val password: String, val token: String)

    private fun call(path: String, method: String, body: JSONObject? = null, token: String? = null): JSONObject? {
        return try {
            val c = URL(BASE + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.setRequestProperty("Content-Type", "application/json")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            c.connectTimeout = 10000
            c.readTimeout = 10000
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.readText() ?: "{}"
            c.disconnect()
            if (code in 200..299) {
                if (text.trim().startsWith("[")) JSONObject().put("items", JSONArray(text)) else JSONObject(text)
            } else null
        } catch (e: Exception) { null }
    }

    /** Creates a fresh disposable inbox and returns its address + login token. */
    fun createAccount(): Account? {
        val domains = call("/domains", "GET") ?: return null
        val list = domains.optJSONArray("items") ?: domains.optJSONArray("hydra:member") ?: return null
        if (list.length() == 0) return null
        val domain = list.getJSONObject(0).optString("domain")
        if (domain.isBlank()) return null

        val user = "pg" + System.currentTimeMillis()
        val pass = "Pg!" + (10000..99999).random()
        val address = "$user@$domain"

        val created = call("/accounts", "POST", JSONObject().put("address", address).put("password", pass))
            ?: return null
        if (created.has("address") || created.optInt("code", 200) < 300) {
            val tokenRes = call("/token", "POST", JSONObject().put("address", address).put("password", pass))
                ?: return null
            val token = tokenRes.optString("token")
            if (token.isBlank()) return null
            return Account(address, pass, token)
        }
        return null
    }

    data class Message(val id: String, val from: String, val subject: String, val intro: String)

    fun listMessages(token: String): List<Message> {
        val res = call("/messages", "GET", token = token) ?: return emptyList()
        val arr = res.optJSONArray("items") ?: res.optJSONArray("hydra:member") ?: return emptyList()
        val out = ArrayList<Message>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val from = m.optJSONObject("from")?.optString("address") ?: ""
            out.add(Message(m.optString("id"), from, m.optString("subject"), m.optString("intro")))
        }
        return out
    }

    fun readMessage(token: String, id: String): String {
        val res = call("/messages/$id", "GET", token = token) ?: return ""
        return res.optString("text", res.optString("html", ""))
    }
}
