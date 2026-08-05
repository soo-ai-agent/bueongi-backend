package io.soo.bueongi.domain.facility

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/** 중심에서의 거리(m)를 붙인 시설 점 — 거리순 정렬·상위 N 절단의 중간 표현. */
internal data class FacilityWithDistance(val point: FacilityPoint, val distanceMeters: Double)
