package io.soo.bueongi.domain.facility
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath

import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 0~100 정수 안전 점수 + 세부 지표. */
data class SafetyScore(
    val score: Int,
    val breakdown: SafetyBreakdown,
)
