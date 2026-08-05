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
 * `POST /share/create` 요청. `expires_in_hours` 는 선택(없으면 DEFAULT_EXPIRES_HOURS).
 * 1~3 외의 값이면 400 VALIDATION_FAILED(기존 공통 에러 계약 재사용).
 */
data class CreateShareRequest(
    @field:Min(MIN_EXPIRES_HOURS.toLong())
    @field:Max(MAX_EXPIRES_HOURS.toLong())
    @JsonProperty("expires_in_hours")
    val expiresInHours: Int? = null,
    /**
     * 예정 도착까지 남은 시간(초). 선택 — 앱이 길안내 시작 시 ETA 를 보내면 서버가 미도착 감시 기준(예정 도착 시각)을 잡는다.
     * 미전송 시 감시 대상에서 제외(하위호환). 음수/0 은 400(양수만 유의미).
     */
    @field:Min(1)
    @JsonProperty("eta_seconds")
    val etaSeconds: Int? = null,
)
