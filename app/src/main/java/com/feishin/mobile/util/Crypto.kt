package com.feishin.mobile.util

import java.security.MessageDigest

fun md5Hex(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

fun normalizeServerUrl(raw: String): String {
    var s = raw.trim()
    require(s.isNotEmpty()) { "Server URL is empty" }
    if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
    s = s.trimEnd('/')
    return "$s/rest/"
}
