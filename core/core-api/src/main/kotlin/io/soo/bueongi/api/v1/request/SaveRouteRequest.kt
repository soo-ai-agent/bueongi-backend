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
 * `POST /share/{token}/route` 요청(앱이 길안내 시작 시 1회). 보호자 페이지가 앱과 동일한 지도
 * (경로선·목적지·안심시설 마커·총 거리/시간)를 그릴 수 있게 경로 컨텍스트를 저장한다.
 *
 * owner_secret 은 위치 갱신과 동일한 쓰기 권한 검증(requireOwner)을 쓴다 — @NotBlank 를 걸지 않고 서비스에서
 * 누락/불일치 모두 404 로 처리한다(공유 URL만 아는 사람의 경로 조작 차단, 토큰 존재 여부 비노출).
 *
 * 좌표 검증 타입은 routes 요청과 공유한다 — 목적지는 [NamedLatLng], 경로선 점은 [LatLng](동일 필드명·검증 규약).
 * 마커만 이 API 고유라 [RouteMarkerRequest] 를 둔다. 응답 쪽 표현은 도메인(ShareResults의 RouteContext)이 소유.
 */
data class SaveRouteRequest(
    @JsonProperty("owner_secret")
    val ownerSecret: String? = null,
    @field:NotNull @field:Valid
    val destination: NamedLatLng?,
    @field:Valid
    @field:Size(max = MAX_ROUTE_PATH_POINTS)
    val path: List<@Valid LatLng>? = null,
    @field:Valid
    @field:Size(max = MAX_ROUTE_MARKERS)
    val markers: List<@Valid RouteMarkerRequest>? = null,
    /** 총 이동 거리(미터). 선택(null 허용) — 음수는 400 으로 거부. */
    @field:Min(0)
    @JsonProperty("total_distance_m")
    val totalDistanceM: Int? = null,
    /** 총 예상 시간(초). 선택(null 허용) — 음수는 400 으로 거부. */
    @field:Min(0)
    @JsonProperty("total_time_s")
    val totalTimeS: Int? = null,
    /**
     * 경로 종류(safe|fast|main). 선택 — 미전송이면 null(기존 동작과 동일). 값이 있으면 허용 집합만 통과하고,
     * 벗어나면 [toStoredRoute] 경계 검증이 422(INVALID_ROUTE_META)를 낸다. @Pattern(400) 대신 경계-검증→422 를
     * 쓰는 이유는 이 계약이 허용 enum 위반을 복구 가능 상태(422)로 규정하기 때문(RouteRequest 의 422 가드와 같은 결).
     */
    val type: String? = null,
    /**
     * 시설 데이터 출처·기준일 문구. 선택 — 미전송이면 null. 최대 [MAX_ROUTE_PROVENANCE_LENGTH]자,
     * 초과 시 [toStoredRoute] 경계 검증이 422(INVALID_ROUTE_META)를 낸다.
     */
    val provenance: String? = null,
) {
    /**
     * 웹 요청 → 저장 모델([StoredRoute]) 매핑. @Valid 가 좌표/마커type/개수를 이미 검증했으므로 lat/lng/marker.type 은
     * 비null 이 보장된다(requireNotNull 은 도달 불가 방어선). 선택 메타(type/provenance)는 @Valid 로 표현 못 하는
     * 값 규약(허용 enum·길이 상한 → 422)이라 여기 경계에서 [validateRouteMeta] 로 검증한다(라우터 수동 검증 금지 준수).
     */
    fun toStoredRoute(): StoredRoute {
        val dest: NamedLatLng = requireNotNull(destination) { "destination 은 @NotNull 검증을 통과했어야 한다" }
        validateRouteMeta()
        return StoredRoute(
            destination = StoredRouteDestination(
                name = dest.name,
                lat = requireNotNull(dest.lat) { "destination.lat 은 검증을 통과했어야 한다" },
                lng = requireNotNull(dest.lng) { "destination.lng 도 동일" },
            ),
            path = path.orEmpty().map {
                StoredCoord(lat = requireNotNull(it.lat), lng = requireNotNull(it.lng))
            },
            markers = markers.orEmpty().map { marker ->
                StoredMarker(
                    type = requireNotNull(marker.type) { "marker.type 은 검증을 통과했어야 한다" },
                    lat = requireNotNull(marker.lat),
                    lng = requireNotNull(marker.lng),
                    x = marker.x,
                    y = marker.y,
                    name = marker.name,
                    purpose = marker.purpose,
                    cameraCount = marker.cameraCount,
                    address = marker.address,
                    phone = marker.phone,
                )
            },
            totalDistanceM = totalDistanceM,
            totalTimeS = totalTimeS,
            type = type,
            provenance = provenance,
        )
    }

    /**
     * 선택 메타(type/provenance) 값 규약 검증(경계 계층). null(미전송)은 통과 — 하위호환(미전송 시 기존 동작 동일).
     * type 은 허용 집합(safe|fast|main) 밖이면, provenance 는 최대 길이 초과면 [InvalidRouteMetaException]→422.
     * (장문 echo 회피를 위해 provenance 위반의 rejectedValue 는 값이 아닌 길이를 담는다.)
     */
    private fun validateRouteMeta() {
        type?.let {
            if (it !in ROUTE_TYPE_VALUES) {
                throw InvalidRouteMetaException(
                    field = "type",
                    message = "type 은 ${ROUTE_TYPE_VALUES.joinToString("|")} 중 하나여야 합니다.",
                    rejectedValue = it,
                )
            }
        }
        provenance?.let {
            if (it.length > MAX_ROUTE_PROVENANCE_LENGTH) {
                throw InvalidRouteMetaException(
                    field = "provenance",
                    message = "provenance 는 최대 ${MAX_ROUTE_PROVENANCE_LENGTH}자까지 입력할 수 있습니다.",
                    rejectedValue = it.length,
                )
            }
        }
    }
}
