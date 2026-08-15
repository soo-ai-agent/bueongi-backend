package io.soo.bueongi.core.domain.share.route

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

data class RouteView(
    val expired: Boolean,
    val route: RouteContext?,
)
