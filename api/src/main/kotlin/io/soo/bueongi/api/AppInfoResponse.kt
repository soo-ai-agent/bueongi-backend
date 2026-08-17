package io.soo.bueongi.api

data class AppInfoResponse(
    val serverVersion: String,
    val serverBuiltAt: String,
    val serverStatus: String,
    val frontendVersion: String,
    val placeSource: String,
    val locationStore: String,
)
