package com.hozinking.hozintv.model

import java.io.Serializable

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String?,
    val userAgent: String?,
    val referer: String?,
    val drmType: String? = null,
    val drmLicenseUrl: String? = null,
    val streamType: String? = null
) : Serializable
