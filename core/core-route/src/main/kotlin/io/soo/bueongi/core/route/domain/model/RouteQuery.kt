package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.core.enums.RouteType

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

data class RouteQuery(
    val origin: GeoPoint,
    val destination: GeoPoint,
    // 좌표만 오면 null 이 가능함
    val destinationName: String?,
    val routeType: RouteType,
    val safetyPreference: SafetyPreference,
)
