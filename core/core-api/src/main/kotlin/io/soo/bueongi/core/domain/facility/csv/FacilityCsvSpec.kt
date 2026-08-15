package io.soo.bueongi.core.domain.facility.csv

import io.soo.bueongi.core.enums.FacilityCoordSystem

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.core.enums.FacilityType

/** 헤더 후보·좌표계 값은 pipeline sources.yaml 의 keyColumns 와 일치시킨다 — 위도/경도 헤더가 있으면 WGS84 우선. */
data class FacilityCsvSpec(
    val type: FacilityType,
    val coordSystem: FacilityCoordSystem,
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
    companion object {
        fun forType(type: FacilityType): FacilityCsvSpec = when (type) {
            FacilityType.CCTV -> FacilityCsvSpec(
                type = type,
                coordSystem = FacilityCoordSystem.WGS84,
                nameHeaders = listOf("관리기관명"),
                addressHeaders = listOf("소재지지번주소"),
                purposeHeaders = listOf("설치목적구분"),
                cameraCountHeaders = listOf("카메라대수"),
                securityPurposeFilter = true,
            )
            FacilityType.SAFEHOUSE -> FacilityCsvSpec(
                type = type,
                coordSystem = FacilityCoordSystem.WGS84,
                nameHeaders = listOf("업소명"),
                addressHeaders = listOf("소재지도로명주소"),
            )
            FacilityType.LAMP -> FacilityCsvSpec(
                type = type,
                coordSystem = FacilityCoordSystem.WGS84,
                nameHeaders = listOf("관리번호"),
                addressHeaders = listOf("설치위치", "소재지지번주소"),
            )
            FacilityType.POLICE -> FacilityCsvSpec(
                type = type,
                coordSystem = FacilityCoordSystem.EPSG5179,
                nameHeaders = listOf("시설명", "관서명"),
                addressHeaders = listOf("주소", "소재지도로명주소"),
                phoneHeaders = listOf("전화번호"),
            )
            FacilityType.BELL -> FacilityCsvSpec(
                type = type,
                coordSystem = FacilityCoordSystem.EPSG5174,
                nameHeaders = listOf("설치장소"),
                addressHeaders = listOf("소재지지번주소"),
            )
            FacilityType.PEDESTRIAN_SIGNAL -> FacilityCsvSpec(
                type = type,
                coordSystem = FacilityCoordSystem.WGS84,
                nameHeaders = listOf("신호등 종류", "신호등종류"),
                addressHeaders = listOf("주소", "소재지지번주소"),
            )
        }
    }
}
