package io.soo.bueongi.domain.facility

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * 최근접 파출소 1건 — 도메인 결과이자 프론트 응답 계약(거리 오름차순 정렬). 동일 필드의 응답 DTO 를
 * 한 벌 더 두는 이중 정의 대신 단일 출처로 유지한다(RouteOption·ShareResults 와 같은 결정).
 * 프론트 NearbyPolice 계약과 동형이라 필드명(tel·distance_m — phone 아님)은 리네임 금지.
 */
data class NearestPolice(
    val name: String?,
    val address: String?,
    val tel: String?,
    val lat: Double,
    val lng: Double,
    @JsonProperty("distance_m")
    val distanceM: Int,
)
