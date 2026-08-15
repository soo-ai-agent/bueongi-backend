package io.soo.bueongi.core.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

data class RoutePoint(
    val lat: Double,
    val lng: Double,
)
