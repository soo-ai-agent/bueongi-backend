package io.soo.bueongi.route

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.facility.GeoPoint

data class RouteQuery(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val destinationName: String?,
    val routeType: RouteType,
    val safetyPreference: SafetyPreference,
)
