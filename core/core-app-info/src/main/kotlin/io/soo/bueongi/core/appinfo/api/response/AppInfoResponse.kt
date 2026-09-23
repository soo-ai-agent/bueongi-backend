package io.soo.bueongi.core.appinfo.api.response

data class AppInfoResponse(
    val serverVersion: String,
    val serverBuiltAt: String,
    val serverStatus: String,
    val frontendVersion: String,
    val locationStore: String,
)
