package io.soo.bueongi.domain.facility

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/** `GET /api/facilities/police/nearest` 응답 봉투 — 프론트 NearbyPolice 계약(거리순 정렬)과 동형. */
data class NearestPoliceResponse(val items: List<NearestPolice>)
