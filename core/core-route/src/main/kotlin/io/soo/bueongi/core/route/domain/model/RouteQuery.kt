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
    /**
     * 안내 중 경로에서 벗어나 다시 찾는 요청인가. 이때는 화면이 대안을 보여 주지 않고
     * 걷던 종류 하나만 골라 쓰므로, 우회 후보를 만들지 않는다(그 호출은 전부 버려진다).
     */
    val reroute: Boolean = false,
)
