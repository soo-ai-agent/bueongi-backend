package io.soo.bueongi.api.v1.request

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.domain.share.LocationReport
import io.soo.bueongi.domain.share.MAX_EXPIRES_HOURS
import io.soo.bueongi.domain.share.MAX_ROUTE_MARKERS
import io.soo.bueongi.domain.share.MAX_ROUTE_PATH_POINTS
import io.soo.bueongi.domain.share.MAX_ROUTE_PROVENANCE_LENGTH
import io.soo.bueongi.domain.share.MIN_EXPIRES_HOURS
import io.soo.bueongi.domain.share.ROUTE_MARKER_TYPE_PATTERN
import io.soo.bueongi.domain.share.ROUTE_TYPE_VALUES
import io.soo.bueongi.support.error.InvalidRouteMetaException
import io.soo.bueongi.storage.db.core.share.StoredCoord
import io.soo.bueongi.storage.db.core.share.StoredMarker
import io.soo.bueongi.storage.db.core.share.StoredRoute
import io.soo.bueongi.storage.db.core.share.StoredRouteDestination
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** `POST /share/{token}/watching` 요청(사용자 본인). owner_secret 으로 본인 확인 — 이 호출은 시청자로 집계하지 않는다. */
data class WatchRequest(
    @field:NotBlank
    @JsonProperty("owner_secret")
    val ownerSecret: String? = null,
)
