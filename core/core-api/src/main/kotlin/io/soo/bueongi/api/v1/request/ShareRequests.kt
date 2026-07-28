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
 * 위치 공유 API 요청 DTO — 검증(@Valid)과 도메인 입력 변환(toReport/toStoredRoute)까지가 이 파일의 책임이다.
 * 도메인 서비스(ShareService)는 웹 DTO 를 모른 채 변환된 값만 받는다(규칙: Controller=변환, 흐름=도메인).
 */

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

/**
 * `POST /share/{token}/end` 요청. 도착/중단 시 공유를 종료한다.
 * 쓰기 권한 비밀(owner_secret)이 일치해야 종료할 수 있다 — 공유 URL만 아는 사람의 임의 종료를 막는다.
 */
data class EndShareRequest(
    @field:NotBlank
    @JsonProperty("owner_secret")
    val ownerSecret: String? = null,
)

/** `POST /share/{token}/watching` 요청(사용자 본인). owner_secret 으로 본인 확인 — 이 호출은 시청자로 집계하지 않는다. */
data class WatchRequest(
    @field:NotBlank
    @JsonProperty("owner_secret")
    val ownerSecret: String? = null,
)

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
        val dest = requireNotNull(destination) { "destination 은 @NotNull 검증을 통과했어야 한다" }
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

/**
 * 경로변 안심시설 마커 요청(검증 계층). 좌표 규약은 [LatLng] 와 동일(범위 + 비유한 거부)이고,
 * type 은 허용 집합만(@Pattern — start/end 는 프론트가 이미 걸러 보낸다). 나머지는 표시용 선택 필드.
 */
data class RouteMarkerRequest(
    @field:NotBlank
    @field:Pattern(regexp = ROUTE_MARKER_TYPE_PATTERN)
    val type: String?,
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
    /** 화면 표시 힌트(%). 앱이 계산한 미니맵 좌표를 그대로 재현하기 위한 선택 값. */
    val x: Double? = null,
    val y: Double? = null,
    val name: String? = null,
    val purpose: String? = null,
    @JsonProperty("camera_count")
    val cameraCount: Int? = null,
    val address: String? = null,
    val phone: String? = null,
)
