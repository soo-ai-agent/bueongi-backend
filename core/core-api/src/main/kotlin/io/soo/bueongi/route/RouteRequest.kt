package io.soo.bueongi.route

import io.soo.bueongi.api.v1.request.LatLng
import io.soo.bueongi.api.v1.request.NamedLatLng
import io.soo.bueongi.core.enums.SafetyPreference
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



data class RouteRequest(
    /** origin 은 의도적으로 @NotNull 미적용 — 누락은 400 이 아니라 [toValidatedQuery] 의 422 ORIGIN_REQUIRED. */
    @field:Valid val origin: LatLng?,
    @field:Valid @field:NotNull val destination: NamedLatLng?,
    val routeType: String? = null,
    val safetyPreference: String? = null,
)

fun RouteRequest.toValidatedQuery(): RouteQuery {
    val origin: GeoPoint = requireOrigin()
    val destinationPoint: GeoPoint = destinationPoint()
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

fun RouteRequest.requireOrigin(): GeoPoint {
    val origin: LatLng = origin ?: throw OriginRequiredException()
    return origin.toGeoPoint()
}

private fun RouteRequest.destinationPoint(): GeoPoint =
    requireNotNull(destination) { "destination 은 @NotNull 검증을 통과했어야 한다" }.toGeoPoint()

private fun LatLng.toGeoPoint(): GeoPoint =
    GeoPoint(requireNotNull(lat) { "lat 은 @NotNull 검증을 통과했어야 한다" }, requireNotNull(lng) { "lng 도 동일" })

private fun NamedLatLng.toGeoPoint(): GeoPoint =
    GeoPoint(requireNotNull(lat) { "lat 은 @NotNull 검증을 통과했어야 한다" }, requireNotNull(lng) { "lng 도 동일" })

fun requireDistinctEndpoints(origin: GeoPoint, destination: GeoPoint) {
    val meters: Double = SpatialMath.haversineMeters(origin, destination)
    if (meters < MIN_TRIP_DISTANCE_METERS) {
        throw SameOriginDestinationException(distanceMeters = meters.roundToInt())
    }
}

const val MIN_TRIP_DISTANCE_METERS: Double = 1.0

fun requireWithinReach(origin: GeoPoint, destination: GeoPoint) {
    val meters: Double = SpatialMath.haversineMeters(origin, destination)
    if (meters > MAX_TRIP_DISTANCE_METERS) {
        throw TripTooFarException(
            distanceMeters = meters.roundToInt(),
            maxMeters = MAX_TRIP_DISTANCE_METERS.roundToInt(),
        )
    }
}

const val MAX_TRIP_DISTANCE_METERS: Double = 100_000.0
