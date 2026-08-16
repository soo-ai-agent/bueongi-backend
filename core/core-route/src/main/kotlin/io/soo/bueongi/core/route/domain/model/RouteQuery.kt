package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.core.enums.RouteType

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

/** 안심 강도는 지정하지 않을 수 있다. */
data class RouteQuery(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val destinationName: String?,
    val routeType: RouteType,
    val safetyPreference: SafetyPreference,
)
