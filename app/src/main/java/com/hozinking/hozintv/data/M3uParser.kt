package com.hozinking.hozintv.data

import com.hozinking.hozintv.model.Channel

/** Parser playlist M3U standar (#EXTM3U / #EXTINF), kompatibel dengan iptv-org. */
object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val vlcOptRegex = Regex("""#EXTVLCOPT:(.+)""")
    private val kodiPropRegex = Regex("""#KODIPROP:([^=]+)=(.+)""")
    private val regionCodeRegex = Regex("""countries/([A-Za-z]{2})""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val pendingKodi = mutableMapOf<String, String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#KODIPROP:")) {
                // Properti Kodi (biasanya sebelum #EXTINF): simpan untuk channel berikut.
                kodiPropRegex.find(line)?.let { m ->
                    pendingKodi[m.groupValues[1].trim()] = m.groupValues[2].trim()
                }
            } else if (line.startsWith("#EXTINF")) {
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
                        // KODIPROP kadang muncul setelah EXTINF: tampung juga.
                        kodiPropRegex.find(next)?.let { m ->
                            pendingKodi[m.groupValues[1].trim()] = m.groupValues[2].trim()
                        }
                        j++
                    } else {
                        url = next
                        break
                    }
                }
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    val drm = extractDrm(pendingKodi)
                    channels += Channel(
                        name = name,
                        url = url,
                        logo = attrs["tvg-logo"]?.ifEmpty { null },
                        group = attrs["group-title"]?.ifEmpty { null },
                        userAgent = userAgent,
                        referer = referer,
                        drmType = drm.first,
                        drmLicenseUrl = drm.second,
                        streamType = detectStreamType(url, pendingKodi)
                    )
                }
                pendingKodi.clear()
                i = j
            }
            i++
        }
        return channels
    }

    /**
     * Ekstrak info DRM dari properti Kodi.
     * Contoh:
     * #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
     * #KODIPROP:inputstream.adaptive.license_key=https://lisensi.contoh/wv|b{SSM}|
     * Nilai license_key diambil sebelum pemisah '|' pertama (format header Kodi).
     */
    private fun extractDrm(props: Map<String, String>): Pair<String?, String?> {
        val licenseType = props["inputstream.adaptive.license_type"]
        val drmType = when {
            licenseType?.contains("widevine", ignoreCase = true) == true -> "widevine"
            licenseType?.contains("playready", ignoreCase = true) == true -> "playready"
            else -> null
        } ?: return null to null
        val licenseUrl = props["inputstream.adaptive.license_key"]
            ?.substringBefore("|")
            ?.trim()
            ?.ifEmpty { null }
        return drmType to licenseUrl
    }

    /** Deteksi tipe stream: dari manifest_type Kodi dulu, lalu ekstensi URL. */
    private fun detectStreamType(url: String, props: Map<String, String>): String {
        val manifest = props["inputstream.adaptive.manifest_type"]
        if (manifest?.contains("hls", ignoreCase = true) == true) return "hls"
        if (manifest?.contains("mpd", ignoreCase = true) == true) return "dash"
        if (url.contains(".m3u8", ignoreCase = true)) return "hls"
        if (url.contains(".mpd", ignoreCase = true)) return "dash"
        return "progressive"
    }

    /**
     * Ambil kode region dari URL playlist pola countries/XX.m3u (data-driven,
     * bukan hardcode). Mengembalikan null bila pola tidak cocok.
     */
    fun extractRegionCode(playlistUrl: String): String? {
        return regionCodeRegex.find(playlistUrl)?.groupValues?.get(1)?.uppercase()
    }
}
