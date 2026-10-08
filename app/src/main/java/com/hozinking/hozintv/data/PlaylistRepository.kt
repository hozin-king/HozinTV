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
        // Daftar channel format JSON ala BitTV (brodatv1/jsonp/mio)
        val BUILT_IN = listOf(
            PlaylistSource("bittv-id", "\uD83C\uDDEE\uD83C\uDDE9 Indonesia", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/ID.json"),
            PlaylistSource("bittv-lo", "\uD83D\uDCFA TV Lokal", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/LO.json"),
            PlaylistSource("bittv-ri", "\uD83D\uDCFA TVRI", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/RI.json"),
            PlaylistSource("bittv-my", "\uD83C\uDDF2\uD83C\uDDFE Malaysia", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/MY.json"),
            PlaylistSource("bittv-sg", "\uD83C\uDDF8\uD83C\uDDEC Singapore", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/SG.json"),
            PlaylistSource("bittv-th", "\uD83C\uDDF9\uD83C\uDDED Thailand", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/TH.json"),
            PlaylistSource("bittv-jp", "\uD83C\uDDEF\uD83C\uDDF5 Japan", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/JP.json"),
            PlaylistSource("bittv-kr", "\uD83C\uDDF0\uD83C\uDDF7 Korea", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/KR.json"),
            PlaylistSource("bittv-au", "\uD83C\uDDE6\uD83C\uDDFA Australia", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/AU.json"),
            PlaylistSource("bittv-gb", "\uD83C\uDDEC\uD83C\uDDE7 UK", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/GB.json"),
            PlaylistSource("bittv-br", "\uD83C\uDDE7\uD83C\uDDF7 Brazil", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/BR.json"),
            PlaylistSource("bittv-sa", "\uD83C\uDDF8\uD83C\uDDE6 Saudi", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/SA.json"),
            PlaylistSource("bittv-sp", "⚽ Sports", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/SP.json"),
            PlaylistSource("bittv-mi", "\uD83C\uDFAC Movies", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/MI.json"),
            PlaylistSource("vod-movies", "\uD83C\uDFAC Film VOD", "https://raw.githubusercontent.com/Zaman-Topu/Ip-tv-Collection/main/FINAL_MOVIES_COMPLETE.m3u"),
            PlaylistSource("bittv-kd", "\uD83E\uDDD2 Kids", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/KD.json"),
            PlaylistSource("bittv-ev", "\uD83C\uDFAA Events", "https://raw.githubusercontent.com/brodatv1/jsonp/master/mio/EV.json"),
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

    fun getLastSourceId(): String = prefs.getString(KEY_LAST, "bittv-id") ?: "bittv-id"
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
        val list = if (text.trimStart().startsWith("{")) BitTvJsonParser.parse(text)
        else M3uParser.parse(text)
        // Perbaiki stream yang diketahui mati/diblokir (URL pengganti + header khusus)
        return StreamFixes.apply(list)
    }
}
