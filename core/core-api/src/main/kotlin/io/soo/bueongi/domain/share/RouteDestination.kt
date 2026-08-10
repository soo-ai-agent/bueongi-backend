package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

data class RouteDestination(
    val name: String? = null,
    val lat: Double,
    val lng: Double,
)
