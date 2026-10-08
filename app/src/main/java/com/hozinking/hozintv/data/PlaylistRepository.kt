package com.hozinking.hozintv.data

import android.content.Context
import com.hozinking.hozintv.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class PlaylistSource(
    val id: String,
    val label: String,
    val url: String,
    val custom: Boolean = false
)

class PlaylistRepository(private val context: Context) {

    companion object {
        val BUILT_IN = listOf(
            PlaylistSource("bittv", "\uD83D\uDCFA BitTV Sports", "https://cdn.jsdelivr.net/gh/duktektv/duktektv/bittv/SP.json"),
        )
        private const val PREFS = "hozintv"
        private const val KEY_CUSTOM = "custom_sources"
        private const val KEY_LAST = "last_source"
    }

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getSources(): List<PlaylistSource> = BUILT_IN + getCustomSources()

    fun getCustomSources(): List<PlaylistSource> {
        return try {
            val arr = JSONArray(prefs.getString(KEY_CUSTOM, "[]") ?: "[]")
            List(arr.length()) { k ->
                val o = arr.getJSONObject(k)
                PlaylistSource("custom$k", o.getString("label"), o.getString("url"), true)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addCustomSource(label: String, url: String) {
        val arr = JSONArray(prefs.getString(KEY_CUSTOM, "[]") ?: "[]")
        arr.put(JSONObject().put("label", label).put("url", url))
        prefs.edit().putString(KEY_CUSTOM, arr.toString()).apply()
    }

    fun removeCustomSource(source: PlaylistSource) {
        try {
            val arr = JSONArray(prefs.getString(KEY_CUSTOM, "[]") ?: "[]")
            val next = JSONArray()
            for (k in 0 until arr.length()) {
                val o = arr.getJSONObject(k)
                if (o.getString("url") != source.url) next.put(o)
            }
            prefs.edit().putString(KEY_CUSTOM, next.toString()).apply()
        } catch (e: Exception) { /* abaikan */ }
    }

    fun getLastSourceId(): String = prefs.getString(KEY_LAST, "bittv") ?: "bittv"
    fun setLastSourceId(id: String) = prefs.edit().putString(KEY_LAST, id).apply()

    /** Download playlist (M3U atau JSON ala BitTV); kalau gagal dan ada cache, pakai cache. */
    suspend fun loadChannels(source: PlaylistSource): List<Channel> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "pl_${source.id}.cache")
        try {
            val conn = URL(source.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) HozinTV/1.0")
            conn.instanceFollowRedirects = true
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            cacheFile.writeText(text)
            parseText(text)
        } catch (e: Exception) {
            if (cacheFile.exists()) parseText(cacheFile.readText())
            else throw e
        }
    }

    private fun parseText(text: String): List<Channel> {
        return if (text.trimStart().startsWith("{")) BitTvJsonParser.parse(text)
        else M3uParser.parse(text)
    }
}
