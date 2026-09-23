package io.soo.bueongi.core.route.api.request

import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.domain.error.OriginRequiredException
import io.soo.bueongi.core.route.domain.error.SameOriginDestinationException
import io.soo.bueongi.core.route.domain.error.TripTooFarException
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import kotlin.math.roundToInt

data class RouteRequest(
    // @NotNull 을 일부러 빼 둔다 — origin 누락은 400 이 아니라 422 ORIGIN_REQUIRED 로 답한다.
    @field:Valid val origin: LatLng?,
    @field:Valid @field:NotNull val destination: NamedLatLng?,
    val routeType: String? = null,
    val safetyPreference: String? = null,
    val reroute: Boolean = false,
) {
    // 값이 들어오면 스스로 검증한다 — 호출부가 변환 함수를 부르지 않아도 미검증 값이 새지 않는다.
    val query: RouteQuery by lazy {
        val originPoint: GeoPoint = geoPointOf(origin?.lat, origin?.lng) ?: throw OriginRequiredException()
        val destinationPoint: GeoPoint =
            requireNotNull(geoPointOf(destination?.lat, destination?.lng)) {
                "destination 은 @NotNull 검증을 통과했어야 한다"
            }
        requireReachableTrip(originPoint, destinationPoint)
        RouteQuery(
            origin = originPoint,
            destination = destinationPoint,
            destinationName = destination?.name,
            routeType = RouteType.of(routeType),
            safetyPreference = SafetyPreference.of(safetyPreference),
            reroute = reroute,
        )
    }

    private fun geoPointOf(
        lat: Double?,
        lng: Double?,
    ): GeoPoint? {
        if (lat == null || lng == null) {
            return null
        }
        return GeoPoint(lat, lng)
    }

    private fun requireReachableTrip(
        origin: GeoPoint,
        destination: GeoPoint,
    ) {
        val meters: Double = origin.distanceMetersTo(destination)
        if (meters < MIN_TRIP_DISTANCE_METERS) {
            throw SameOriginDestinationException(distanceMeters = meters.roundToInt())
        }
        if (meters > MAX_TRIP_DISTANCE_METERS) {
            throw TripTooFarException(
                distanceMeters = meters.roundToInt(),
                maxMeters = MAX_TRIP_DISTANCE_METERS.roundToInt(),
            )
        }
    }

    private companion object {
        const val MIN_TRIP_DISTANCE_METERS: Double = 1.0
        const val MAX_TRIP_DISTANCE_METERS: Double = 100_000.0
    }
}
