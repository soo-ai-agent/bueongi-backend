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

/**
 * `POST /share/{token}/end` 요청. 도착/중단 시 공유를 종료한다.
 * 쓰기 권한 비밀(owner_secret)이 일치해야 종료할 수 있다 — 공유 URL만 아는 사람의 임의 종료를 막는다.
 */
data class EndShareRequest(
    @field:NotBlank
    @JsonProperty("owner_secret")
    val ownerSecret: String? = null,
)
