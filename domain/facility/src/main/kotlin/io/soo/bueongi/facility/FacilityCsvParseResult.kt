package io.soo.bueongi.facility

import io.soo.bueongi.facility.coord.Epsg5174
import io.soo.bueongi.facility.coord.Epsg5179
import io.soo.bueongi.facility.coord.Wgs84Point
import io.soo.bueongi.storage.db.core.facility.CCTV_SECURITY_PURPOSES
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class FacilityCsvParseResult(
    val coordSystem: FacilityCoordSystem,
    val received: Int,
    val points: List<FacilityPoint>,
) {
    val skipped: Int get() = received - points.size
}
