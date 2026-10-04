package com.hozinking.hozintv.data

import com.hozinking.hozintv.model.Channel

/** Parser playlist M3U standar (#EXTM3U / #EXTINF), kompatibel dengan iptv-org. */
object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val vlcOptRegex = Regex("""#EXTVLCOPT:(.+)""")

    fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXTINF")) {
                val attrs = attrRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                val name = line.substringAfterLast(",").trim().ifEmpty { "Tanpa Nama" }
                var userAgent = attrs["http-user-agent"]?.ifEmpty { null }
                var referer = attrs["http-referrer"]?.ifEmpty { null }
                // URL ada di baris berikutnya (lewati baris #...)
                var j = i + 1
                var url: String? = null
                while (j < lines.size) {
                    val next = lines[j]
                    if (next.startsWith("#")) {
                        vlcOptRegex.find(next)?.let { m ->
                            val opt = m.groupValues[1]
                            if (opt.startsWith("http-user-agent=") && userAgent == null)
                                userAgent = opt.substringAfter("=").ifEmpty { null }
                            if (opt.startsWith("http-referrer=") && referer == null)
                                referer = opt.substringAfter("=").ifEmpty { null }
                        }
                        j++
                    } else {
                        url = next
                        break
                    }
                }
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    channels += Channel(
                        name = name,
                        url = url,
                        logo = attrs["tvg-logo"]?.ifEmpty { null },
                        group = attrs["group-title"]?.ifEmpty { null },
                        userAgent = userAgent,
                        referer = referer
                    )
                }
                i = j
            }
            i++
        }
        return channels
    }
}
