package io.soo.bueongi.core.api.controller

import io.soo.bueongi.core.domain.place.RemotePlaceProvider
import io.soo.bueongi.core.domain.share.ShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class HealthStatus(
    val status: String,
    val service: String,
    val placeSource: String,
    val locationStore: String,
)
