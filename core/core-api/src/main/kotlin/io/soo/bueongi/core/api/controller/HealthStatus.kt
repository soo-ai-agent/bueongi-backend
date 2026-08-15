package io.soo.bueongi.core.api.controller

data class HealthStatus(
    val status: String,
    val service: String,
    val placeSource: String,
    val locationStore: String,
)
