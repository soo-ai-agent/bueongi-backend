package io.soo.bueongi.domain.facility

import io.soo.bueongi.client.safemap.Epsg5174
import io.soo.bueongi.client.safemap.Epsg5179
import io.soo.bueongi.client.safemap.Wgs84Point
import io.soo.bueongi.storage.db.core.facility.CCTV_SECURITY_PURPOSES
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * CSV 파싱 결과. [coordSystem] 은 실제 사용된 좌표계(헤더에 위도/경도가 있으면 WGS84, 없으면 spec 좌표계),
 * [received] 는 데이터 행 수, [points] 는 유효 시설. [skipped] 는 좌표 없음·불량·비방범 CCTV 등으로 버려진 행 수.
 */
data class FacilityCsvParseResult(
    val coordSystem: FacilityCoordSystem,
    val received: Int,
    val points: List<FacilityPoint>,
) {
    val skipped: Int get() = received - points.size
}
