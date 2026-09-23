package io.soo.bueongi.core.route.api.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.domain.error.CoordinateOutOfRangeException
import io.soo.bueongi.core.route.domain.error.SameOriginDestinationException
import io.soo.bueongi.core.route.domain.error.TripTooFarException
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import kotlin.math.roundToInt

class RouteRequest
    @JsonCreator
    constructor(
        @param:JsonProperty(value = "origin", required = true) private val rawOrigin: LatLng,
        @param:JsonProperty(value = "destination", required = true) private val rawDestination: NamedLatLng,
        @param:JsonProperty(value = "routeType") private val rawRouteType: String = "",
        @param:JsonProperty(value = "safetyPreference") private val rawSafetyPreference: String = "",
        @param:JsonProperty(value = "reroute") val reroute: Boolean = false,
    ) {
        val origin: GeoPoint = requireInRange(rawOrigin.lat, rawOrigin.lng, "origin")
        val destination: GeoPoint = requireInRange(rawDestination.lat, rawDestination.lng, "destination")
        val destinationName: String = rawDestination.name
        val routeType: RouteType = RouteType.of(rawRouteType)
        val safetyPreference: SafetyPreference = SafetyPreference.of(rawSafetyPreference)

        init {
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

        private fun requireInRange(lat: Double, lng: Double, field: String): GeoPoint {
            if (!GeoPoint.isValidLat(lat)) {
                throw CoordinateOutOfRangeException("$field.lat", lat)
            }
            if (!GeoPoint.isValidLng(lng)) {
                throw CoordinateOutOfRangeException("$field.lng", lng)
            }
            return GeoPoint(lat, lng)
        }

        private companion object {
            const val MIN_TRIP_DISTANCE_METERS: Double = 1.0
            const val MAX_TRIP_DISTANCE_METERS: Double = 100_000.0
        }
    }
