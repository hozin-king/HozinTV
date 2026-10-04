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
            PlaylistSource("id", "\uD83C\uDDEE\uD83C\uDDE9 Indonesia", "https://iptv-org.github.io/iptv/countries/id.m3u"),
            PlaylistSource("sports", "⚽ Sports", "https://iptv-org.github.io/iptv/categories/sports.m3u"),
            PlaylistSource("movies", "\uD83C\uDFAC Movies", "https://iptv-org.github.io/iptv/categories/movies.m3u"),
            PlaylistSource("news", "\uD83D\uDCF0 News", "https://iptv-org.github.io/iptv/categories/news.m3u"),
            PlaylistSource("music", "\uD83C\uDFB5 Music", "https://iptv-org.github.io/iptv/categories/music.m3u"),
            PlaylistSource("kids", "\uD83E\uDDD2 Kids", "https://iptv-org.github.io/iptv/categories/kids.m3u"),
            PlaylistSource("all", "\uD83C\uDF0D Semua Channel", "https://iptv-org.github.io/iptv/index.m3u"),
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

    fun getLastSourceId(): String = prefs.getString(KEY_LAST, "id") ?: "id"
    fun setLastSourceId(id: String) = prefs.edit().putString(KEY_LAST, id).apply()

    /** Download playlist; kalau gagal dan ada cache, pakai cache. */
    suspend fun loadChannels(source: PlaylistSource): List<Channel> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "pl_${source.id}.m3u")
        try {
            val conn = URL(source.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) HozinTV/1.0")
            conn.instanceFollowRedirects = true
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            if (!text.contains("#EXTM3U")) throw IllegalStateException("Bukan playlist M3U")
            cacheFile.writeText(text)
            M3uParser.parse(text)
        } catch (e: Exception) {
            if (cacheFile.exists()) M3uParser.parse(cacheFile.readText())
            else throw e
        }
    }
}
