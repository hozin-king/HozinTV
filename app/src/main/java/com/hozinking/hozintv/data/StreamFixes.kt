package com.hozinking.hozintv.data

import com.hozinking.hozintv.model.Channel

/**
 * Perbaikan stream yang diketahui mati/diblokir di daftar BitTV.
 * Diverifikasi 8 Okt 2026: tiap URL pengganti dicek HTTP 200 + playlist valid,
 * header khusus (User-Agent/Referer) dites satu per satu.
 *
 * Cara kerja: [apply] dipanggil di PlaylistRepository.parseText, jadi berlaku
 * untuk semua sumber (BitTV JSON, M3U custom, cache). Pencocokan pakai nama
 * channel yang dinormalisasi (huruf kecil, alfanumerik saja).
 */
object StreamFixes {

    private const val UA_CHROME_65 =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36"
    private const val UA_CHROME_120 =
        "Mozilla/5.0 Windows NT 10.0; Win64; x64 AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val REF_DENS = "https://www.dens.tv/"

    private data class Fix(
        val url: String? = null,
        val userAgent: String? = null,
        val referer: String? = null
    )

    private val fixes: Map<String, Fix> = mapOf(
        // Grup MNC/Media Nusantara — server lama 403, ganti ke mirror yang hidup
        "rcti" to Fix(url = "http://202.150.172.59/RCTIHD/index.m3u8"),
        "sctvdigitaltv" to Fix(url = "http://202.150.172.59/SCTV/tracks-v1a1/mono.m3u8"),
        "sctv" to Fix(url = "http://202.150.172.59/SCTV/tracks-v1a1/mono.m3u8"),
        "inews" to Fix(url = "http://202.150.172.59/iNews/index.m3u8"),
        "mnctv" to Fix(url = "http://202.150.172.59/MNCTV/tracks-v1a1/mono.m3u8"),
        "mnctvhd" to Fix(url = "http://202.150.172.59/MNCTV/tracks-v1a1/mono.m3u8"),
        // ANTV — butuh User-Agent browser, kalau tidak 403
        "antvhd" to Fix(url = "http://103.58.160.157:8278/720-ANTV/playlist.m3u8", userAgent = UA_CHROME_65),
        "antv" to Fix(url = "http://103.58.160.157:8278/720-ANTV/playlist.m3u8", userAgent = UA_CHROME_65),
        // Metro TV — edge resmi Medcom
        "metrotv" to Fix(url = "https://edge.medcom.id/live-edge/smil:metro.smil/playlist.m3u8"),
        // Grup DensTV — butuh Referer + UA, geo-block di luar Indonesia
        "kompastv" to Fix(
            url = "https://op-group1-swiftservehd-1.dens.tv/h/h234/index.m3u8",
            referer = REF_DENS, userAgent = UA_CHROME_120
        ),
        "mojidigitaltv" to Fix(
            url = "https://op-group1-swiftservehd-1.dens.tv/h/h207/index.m3u8",
            referer = REF_DENS, userAgent = UA_CHROME_120
        ),
        "moji" to Fix(
            url = "https://op-group1-swiftservehd-1.dens.tv/h/h207/index.m3u8",
            referer = REF_DENS, userAgent = UA_CHROME_120
        ),
        "tvone" to Fix(
            url = "https://op-group1-swiftservehd-1.dens.tv/h/h40/index.m3u8",
            referer = REF_DENS, userAgent = UA_CHROME_120
        ),
    )

    private fun norm(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun detectStreamType(url: String): String? = when {
        url.contains(".mpd", ignoreCase = true) -> "dash"
        url.contains(".m3u8", ignoreCase = true) -> "hls"
        else -> null
    }

    fun apply(channels: List<Channel>): List<Channel> = channels.map { ch ->
        val fix = fixes[norm(ch.name)] ?: return@map ch
        val newUrl = fix.url ?: ch.url
        ch.copy(
            url = newUrl,
            userAgent = fix.userAgent ?: ch.userAgent,
            referer = fix.referer ?: ch.referer,
            // URL pengganti bisa beda format (DASH -> HLS), deteksi ulang
            streamType = if (fix.url != null) detectStreamType(newUrl) else ch.streamType
        )
    }
}
