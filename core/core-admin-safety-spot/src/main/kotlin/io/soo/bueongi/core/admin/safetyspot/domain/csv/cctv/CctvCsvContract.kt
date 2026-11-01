package io.soo.bueongi.core.admin.safetyspot.domain.csv.cctv

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.CsvColumns
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCoordSystem
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvContract
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.store.CsvSkipReason

// 전국 CCTV 표준데이터 — WGS84. 방범 목적(생활방범·다목적·어린이보호·차량방범) 행만 적재한다.
class CctvCsvContract : SafetySpotCsvContract(
    type = SafetySpotType.CCTV,
    coordSystem = SafetySpotCoordSystem.WGS84,
    latHeaders = listOf("WGS84위도", "위도"),
    lngHeaders = listOf("WGS84경도", "경도"),
    nameHeaders = listOf("관리기관명"),
    addressHeaders = listOf("소재지지번주소"),
    purposeHeaders = listOf("설치목적구분"),
    cameraCountHeaders = listOf("카메라대수"),
) {
    override fun skipReasonOf(row: List<String>, columns: CsvColumns): CsvSkipReason? {
        if (columns.purpose(row) !in CCTV_SECURITY_PURPOSES) {
            return CsvSkipReason.NOT_SECURITY_PURPOSE
        }
        return null
    }

    companion object {
        val CCTV_SECURITY_PURPOSES: Set<String> = setOf("생활방범", "다목적", "어린이보호", "차량방범")
    }
}
