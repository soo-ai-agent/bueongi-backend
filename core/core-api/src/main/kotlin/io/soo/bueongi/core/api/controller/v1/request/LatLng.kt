package io.soo.bueongi.core.api.controller.v1.request

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.domain.route.RouteQuery
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath
import io.soo.bueongi.core.support.error.OriginRequiredException
import io.soo.bueongi.core.support.error.SameOriginDestinationException
import io.soo.bueongi.core.support.error.TripTooFarException
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import kotlin.math.roundToInt

data class LatLng(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
)
