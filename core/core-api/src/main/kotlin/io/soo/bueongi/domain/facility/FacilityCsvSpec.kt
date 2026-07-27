package io.soo.bueongi.domain.facility

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.core.enums.FacilityType

/**
 * 시설 종류별 표준 CSV 매핑 규격 — 어느 헤더에서 어떤 필드를 읽고, 좌표가 어느 좌표계인지 정의한다.
 * 값은 pipeline `sources.yaml` 의 `keyColumns`(표준데이터 컬럼명)와 일치시킨다. [FacilityCsvParser] 가 이 규격으로
 * 각 행을 저장 모델로 옮긴다.
 *
 * 헤더는 후보 목록이다 — 원천마다 표기가 조금씩 다를 수 있어(예: lamp 주소=설치위치|소재지지번주소) 앞에서부터
 * 먼저 존재하는 헤더를 쓴다. 좌표는 종류의 기본 좌표계([coordSystem])를 쓰되, 어떤 종류든 `위도/경도` 헤더가
 * 있으면 그것을 우선한다(WGS84 폴백) — 표준데이터가 좌표를 직접 주는 경우를 견고하게 흡수한다.
 */
data class FacilityCsvSpec(
    val type: FacilityType,
    /** 투영 x/y 를 쓸 때의 좌표계. `위도/경도` 헤더가 있으면 이 값과 무관하게 WGS84 로 읽는다. */
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
    /** CCTV 방범 용도(설치목적구분) 필터 적용 여부 — 부팅 CSV 적재와 동일 기준. */
    val securityPurposeFilter: Boolean = false,
) {
    companion object {
        /** 종류별 표준 CSV 규격. */
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
        }
    }
}

/**
 * 업로드 CSV 좌표계 — 응답의 `coordSystem` 문자열([wireName])이자 파서의 좌표 변환 분기 키.
 * [wireName] 이 와이어 값이다([JsonValue]) — 업로드 응답의 coordSystem 필드가 이 값이라 직렬화가
 * 정확히 wgs84|epsg5179|epsg5174 를 내야 한다. 역직렬화는 [from] 으로 고정한다([JsonCreator]).
 */
enum class FacilityCoordSystem(@get:JsonValue val wireName: String) {
    WGS84("wgs84"),
    EPSG5179("epsg5179"),
    EPSG5174("epsg5174"),
    ;

    companion object {
        /** wireName 문자열 → 좌표계. 알 수 없는 값은 예외(닫힌 집합) — 역직렬화 경계용. */
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityCoordSystem = entries.first { it.wireName == value }
    }
}
