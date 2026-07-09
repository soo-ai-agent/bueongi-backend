package io.soo.bueongi.api.v1.request

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.domain.route.RouteQuery
import io.soo.bueongi.domain.route.RouteType
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath
import io.soo.bueongi.support.error.OriginRequiredException
import io.soo.bueongi.support.error.SameOriginDestinationException
import io.soo.bueongi.support.error.TripTooFarException
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import kotlin.math.roundToInt

/** 좌표(위경도). 위도 -90~90, 경도 -180~180. */
data class LatLng(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
)

/** 목적지 좌표 + 표시명. */
data class NamedLatLng(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
    val name: String? = null,
)

/** POST /api/routes/compare·safe-compare·facilities 공통 요청(출발/도착). */
data class RouteRequest(
    /**
     * 출발지. 누락(위치 권한 거부/미지원)은 "잘못된 입력"이 아니라 복구 가능 상태이므로 @NotNull 로
     * 400 을 내지 않고, [toValidatedQuery] 가 422 `ORIGIN_REQUIRED` 를 낸다.
     * 단 값이 있으면 @Valid 로 좌표 범위를 검증한다(범위 초과는 여전히 400).
     */
    @field:Valid val origin: LatLng?,
    @field:Valid @field:NotNull val destination: NamedLatLng?,
    /**
     * 안심시설 조회 시 어떤 경로의 시설을 볼지(safe/main/fast). null/미상이면 safe 로 본다.
     * 경로 비교(compare)에서는 무시된다.
     */
    val routeType: String? = null,
    /**
     * 안심 강도 선호(safe-compare 전용): "balanced"|"safe"|"safest" → 우회 허용 예산 0.2/0.35/0.6.
     * null/미상이면 "safe"(0.35). compare/facilities 에서는 무시된다.
     */
    val safetyPreference: String? = null,
)

/**
 * compare/safe-compare/facilities 공용 입력 가드(단일 진실 원천) — 검증을 통과하면 비null 좌표의
 * 도메인 질의([RouteQuery])를 돌려준다. 출발지 확보(422 `ORIGIN_REQUIRED`) →
 * 출발≠도착(422 `SAME_ORIGIN_DESTINATION`) → 원거리 상한(422 `TRIP_TOO_FAR`) 을 **항상 같은 순서로** 적용한다.
 *
 * 세 엔드포인트가 각자 가드를 나열하면 한쪽에만 가드를 추가/수정해 검증이 어긋날 위험이 있다 —
 * 안심앱에서 한 엔드포인트만 원거리/동일좌표를 거른다면 안전 계약이 깨진다. 그 드리프트를 막기 위해
 * 가드 묶음·순서를 여기 한 곳에만 둔다. 반환이 [GeoPoint] 기반이라 도메인에서 널 단언(!!)이 필요 없다.
 */
fun RouteRequest.toValidatedQuery(): RouteQuery {
    val origin = requireOrigin()
    val destinationPoint = destinationPoint()
    requireDistinctEndpoints(origin, destinationPoint)
    requireWithinReach(origin, destinationPoint)
    return RouteQuery(
        origin = origin,
        destination = destinationPoint,
        destinationName = destination?.name,
        routeType = RouteType.from(routeType),
        safetyPreference = SafetyPreference.from(safetyPreference),
    )
}

/**
 * 출발지를 반환하되, 없으면 [OriginRequiredException]→422 `ORIGIN_REQUIRED`.
 * destination 은 항상 필요하므로 @NotNull(누락 시 400)로 두고, origin 만 이렇게 구분 처리한다.
 */
fun RouteRequest.requireOrigin(): GeoPoint {
    val origin = origin ?: throw OriginRequiredException()
    return origin.toGeoPoint()
}

/** @Valid(@NotNull) 통과 후에만 호출된다 — requireNotNull 은 도달 불가 방어선(검증 우회 버그 표면화). */
private fun RouteRequest.destinationPoint(): GeoPoint =
    requireNotNull(destination) { "destination 은 @NotNull 검증을 통과했어야 한다" }.toGeoPoint()

private fun LatLng.toGeoPoint(): GeoPoint =
    GeoPoint(requireNotNull(lat) { "lat 은 @NotNull 검증을 통과했어야 한다" }, requireNotNull(lng) { "lng 도 동일" })

private fun NamedLatLng.toGeoPoint(): GeoPoint =
    GeoPoint(requireNotNull(lat) { "lat 은 @NotNull 검증을 통과했어야 한다" }, requireNotNull(lng) { "lng 도 동일" })

/**
 * 출발지·도착지가 사실상 같은 지점이면(직선거리 < [MIN_TRIP_DISTANCE_METERS]) [SameOriginDestinationException]
 * →422 `SAME_ORIGIN_DESTINATION`. 같은 지점으로의 경로 비교/안심시설은 의미가 없고, GPS가 출발=도착으로 잡혔거나
 * 도착지를 잘못 고른 복구 가능 상태다.
 */
fun requireDistinctEndpoints(origin: GeoPoint, destination: GeoPoint) {
    val meters = SpatialMath.haversineMeters(origin, destination)
    if (meters < MIN_TRIP_DISTANCE_METERS) {
        throw SameOriginDestinationException(distanceMeters = meters.roundToInt())
    }
}

/**
 * 출발지=도착지로 간주하는 최소 직선거리(미터). 이 미만이면 사실상 같은 지점(GPS 정밀도 이하)으로 보고 거부한다.
 * 정상적인 도보 귀가 경로는 수십 m 이상이므로 근거리 이동을 막지 않으면서 동일 좌표만 차단하도록 작게 잡는다.
 */
const val MIN_TRIP_DISTANCE_METERS = 1.0

/**
 * 출발지-도착지 직선거리가 [MAX_TRIP_DISTANCE_METERS]를 넘으면 [TripTooFarException]→422 `TRIP_TOO_FAR`.
 * 도보 귀가 앱에서 수백 km 떨어진 도착지는 정상 입력이 아니라 거의 항상 도착지 오선택/위경도 뒤바뀜·타 지역의
 * 캐시된 GPS 같은 실수다. 무의미한 장거리 경로를 만들기보다 복구 가능한 422 로 거부해 도착지 재선택을 유도한다.
 * [requireDistinctEndpoints] 와 짝을 이뤄 거리의 하한·상한을 함께 막는다.
 */
fun requireWithinReach(origin: GeoPoint, destination: GeoPoint) {
    val meters = SpatialMath.haversineMeters(origin, destination)
    if (meters > MAX_TRIP_DISTANCE_METERS) {
        throw TripTooFarException(
            distanceMeters = meters.roundToInt(),
            maxMeters = MAX_TRIP_DISTANCE_METERS.roundToInt(),
        )
    }
}

/**
 * 도보 귀가 경로로 허용하는 최대 출발지-도착지 직선거리(미터). 100km — 한 도시 안의 어떤 귀가도 넘지 않으면서
 * 도시 간(예: 서울-부산 ~325km) 좌표 오선택은 분명히 거른다. 정상 입력을 막지 않도록 넉넉히 잡되 비현실적 입력만 차단.
 */
const val MAX_TRIP_DISTANCE_METERS = 100_000.0
