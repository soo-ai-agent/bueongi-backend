package io.soo.bueongi.api.v1

import io.soo.bueongi.place.RemotePlaceProvider
import io.soo.bueongi.share.ShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class HealthStatus(
    val status: String,
    val service: String,
    val placeSource: String,
    val locationStore: String,
)
