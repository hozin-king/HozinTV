package com.hozinking.hozintv.data

import com.hozinking.hozintv.model.Channel
import org.json.JSONObject

/**
 * Parser daftar channel format JSON ala BitTV (channel_list_url):
 * { "country_name": "...", "info": [ { "name", "hls", "image", ... } ] }
 * Field "hls" bisa berisi URL HLS (.m3u8), DASH (.mpd), atau progressive.
 * Logo bisa berupa URL http atau data URI base64.
 */
object BitTvJsonParser {

    fun parse(text: String): List<Channel> {
        val root = JSONObject(text)
        val group = root.optString("country_name", "")
            .ifBlank { root.optString("country", "") }
        val arr = root.optJSONArray("info") ?: return emptyList()
        val out = ArrayList<Channel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("hls", "").trim()
            if (url.isEmpty()) continue
            val name = o.optString("name", "")
                .ifBlank { o.optString("namespace", "Channel ${i + 1}") }
            val logo = o.optString("image", "").ifBlank { null }
            out.add(
                Channel(
                    name = name,
                    url = url,
                    logo = logo,
                    group = group.ifBlank { null },
                    userAgent = null,
                    referer = null,
                    streamType = detectStreamType(url)
                )
            )
        }
        return out
    }

    private fun detectStreamType(url: String): String? = when {
        url.contains(".mpd", ignoreCase = true) -> "dash"
        url.contains(".m3u8", ignoreCase = true) -> "hls"
        else -> null
    }
}
