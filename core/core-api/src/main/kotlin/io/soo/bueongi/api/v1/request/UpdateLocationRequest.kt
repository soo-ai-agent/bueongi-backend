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
 * `POST /share/{token}/location` 요청(사용자 단말, 5초마다). 좌표 규약은 routes 의 LatLng 와 동일 —
 * 범위(@DecimalMin/@DecimalMax) + 비유한(NaN/±Inf) 거부(Jackson 기본). MVP 계약은 `{lat, lng}` 만 받는다.
 */
data class UpdateLocationRequest(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
    /** 쓰기 권한 비밀(앱만 보유). 누락 시 400, 불일치 시 404 — 공유 URL만 아는 사람은 위치를 쓸 수 없다. */
    @field:NotBlank
    @JsonProperty("owner_secret")
    val ownerSecret: String? = null,
    /**
     * 목적지까지 남은 거리(미터). 선택 — 앱이 길안내 중 매 위치 보고에 실어 보내면 보호자 지도가 잔여 거리를 표시한다.
     * 누락 시 기존 동작(잔여 정보 없음)과 동일하고, 음수는 400 으로 거부한다. 상한은 없다(장거리 경로 허용).
     */
    @field:DecimalMin("0.0")
    @JsonProperty("remaining_m")
    val remainingM: Double? = null,
    /** 목적지까지 남은 예상 시간(초). [remainingM] 과 짝을 이루는 선택 필드(누락 무해, 음수 400 거부). */
    @field:DecimalMin("0.0")
    @JsonProperty("remaining_s")
    val remainingS: Double? = null,
) {
    /** @Valid(@NotNull) 통과 후 호출 — 도메인 입력(비null 좌표)으로 변환. requireNotNull 은 도달 불가 방어선. */
    fun toReport(): LocationReport = LocationReport(
        lat = requireNotNull(lat) { "lat 은 @NotNull 검증을 통과했어야 한다" },
        lng = requireNotNull(lng) { "lng 도 동일" },
        remainingM = remainingM,
        remainingS = remainingS,
    )
}
