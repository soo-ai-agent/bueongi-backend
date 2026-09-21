package io.soo.bueongi.api.config

import jakarta.servlet.http.HttpServletRequest

fun clientAddressOf(request: HttpServletRequest): String {
    val forwarded: String? = request.getHeader(FORWARDED_FOR_HEADER)
    val original: String? = forwarded?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    return original ?: request.remoteAddr ?: UNKNOWN_ADDRESS
}

private const val FORWARDED_FOR_HEADER: String = "X-Forwarded-For"

private const val UNKNOWN_ADDRESS: String = "unknown"
