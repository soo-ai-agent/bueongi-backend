package io.soo.bueongi.domain.facility
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.GridSpatialIndex
import io.soo.bueongi.storage.db.core.facility.SegmentProjection
import io.soo.bueongi.storage.db.core.facility.SpatialMath

import org.springframework.stereotype.Component
import kotlin.math.floor

/** 안심 앵커: 클러스터 중심 좌표 + 구성 시설수(weight). */
data class SafeAnchor(
    val center: GeoPoint,
    val weight: Int,
)
