package io.soo.bueongi.core.safetyspot.domain.csv.model

import io.soo.bueongi.core.enums.SafetySpotCoordSystem

import io.soo.bueongi.core.enums.SafetySpotType

/**
 * 헤더 후보·좌표계 값은 pipeline sources.yaml 의 keyColumns 와 일치시킨다 — 위도/경도 헤더가 있으면 WGS84 우선.
 */
data class SafetySpotCsvSpec(
    val type: SafetySpotType,
    val coordSystem: SafetySpotCoordSystem,
    val latHeaders: List<String> = listOf("위도"),
    val lngHeaders: List<String> = listOf("경도"),
    val xHeaders: List<String> = listOf("X좌표"),
    val yHeaders: List<String> = listOf("Y좌표"),
    val nameHeaders: List<String> = emptyList(),
    val addressHeaders: List<String> = emptyList(),
    val purposeHeaders: List<String> = emptyList(),
    val cameraCountHeaders: List<String> = emptyList(),
    val phoneHeaders: List<String> = emptyList(),
    val securityPurposeFilter: Boolean = false,
) {
    /** 헤더 이름표에서 이 규격의 열 위치를 찾는다. 위도/경도 헤더가 있으면 좌표계를 WGS84 로 본다. */
    fun locateColumns(headerIndex: Map<String, Int>): CsvColumns {
        val latIdx: Int? = headerIndex.firstIndexOf(latHeaders)
        val lngIdx: Int? = headerIndex.firstIndexOf(lngHeaders)
        val useWgs84: Boolean = latIdx != null && lngIdx != null
        return CsvColumns(
            useWgs84 = useWgs84,
            coordSystem = if (useWgs84) SafetySpotCoordSystem.WGS84 else coordSystem,
            latIndex = latIdx,
            lngIndex = lngIdx,
            xIndex = headerIndex.firstIndexOf(xHeaders),
            yIndex = headerIndex.firstIndexOf(yHeaders),
            nameIndex = headerIndex.firstIndexOf(nameHeaders),
            addressIndex = headerIndex.firstIndexOf(addressHeaders),
            purposeIndex = headerIndex.firstIndexOf(purposeHeaders),
            cameraCountIndex = headerIndex.firstIndexOf(cameraCountHeaders),
            phoneIndex = headerIndex.firstIndexOf(phoneHeaders),
        )
    }

    private fun Map<String, Int>.firstIndexOf(candidates: List<String>): Int? =
        candidates.firstNotNullOfOrNull { this[it] }

    companion object {
        fun forType(type: SafetySpotType): SafetySpotCsvSpec = when (type) {
            SafetySpotType.CCTV -> SafetySpotCsvSpec(
                type = type,
                coordSystem = SafetySpotCoordSystem.WGS84,
                nameHeaders = listOf("관리기관명"),
                addressHeaders = listOf("소재지지번주소"),
                purposeHeaders = listOf("설치목적구분"),
                cameraCountHeaders = listOf("카메라대수"),
                securityPurposeFilter = true,
            )
            SafetySpotType.SAFEHOUSE -> SafetySpotCsvSpec(
                type = type,
                coordSystem = SafetySpotCoordSystem.WGS84,
                nameHeaders = listOf("업소명"),
                addressHeaders = listOf("소재지도로명주소"),
            )
            SafetySpotType.STREET_LAMP, SafetySpotType.SECURITY_LAMP -> SafetySpotCsvSpec(
                type = type,
                coordSystem = SafetySpotCoordSystem.WGS84,
                nameHeaders = listOf("관리번호"),
                addressHeaders = listOf("설치위치", "소재지지번주소"),
            )
            SafetySpotType.CRIME_PRONE_AREA -> SafetySpotCsvSpec(
                type = type,
                coordSystem = SafetySpotCoordSystem.WGS84,
                nameHeaders = listOf("시설명", "지역명"),
                addressHeaders = listOf("주소", "소재지지번주소"),
            )
            SafetySpotType.POLICE -> SafetySpotCsvSpec(
                type = type,
                coordSystem = SafetySpotCoordSystem.EPSG5179,
                nameHeaders = listOf("시설명", "관서명"),
                addressHeaders = listOf("주소", "소재지도로명주소"),
                phoneHeaders = listOf("전화번호"),
            )
            SafetySpotType.BELL -> SafetySpotCsvSpec(
                type = type,
                coordSystem = SafetySpotCoordSystem.EPSG5174,
                nameHeaders = listOf("설치장소"),
                addressHeaders = listOf("소재지지번주소"),
            )
            SafetySpotType.PEDESTRIAN_SIGNAL -> SafetySpotCsvSpec(
                type = type,
                coordSystem = SafetySpotCoordSystem.WGS84,
                nameHeaders = listOf("신호등 종류", "신호등종류"),
                addressHeaders = listOf("주소", "소재지지번주소"),
            )
        }
    }
}
