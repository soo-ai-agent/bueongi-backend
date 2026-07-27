package io.soo.bueongi.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 경로상 POI 종류 — 프론트 MapMock POI type 어휘. [wire] 문자열이 곧 응답 계약이라 [JsonValue] 로 못 박는다.
 *
 * start/end 는 안심시설 집계에서 제외한다(프론트 summarizeSafetyFacilities 와 동일 규칙).
 * [STORE](편의점)는 사용자 결정으로 지도에서 제외되어 **실제로는 마커가 생성되지 않지만**(요약 store 는 항상 0),
 * 프론트 계약의 유형 집합에 포함되므로 어휘로 남긴다. 역직렬화는 [from] 으로 고정한다([JsonCreator]).
 */
enum class FacilityPoiType(@get:JsonValue val wire: String) {
    START("start"),
    END("end"),
    CCTV("cctv"),
    BELL("bell"),
    STORE("store"),
    POLICE("police"),
    SAFEHOUSE("safehouse"),
    LAMP("lamp"),
    ;

    companion object {
        /** POI type 문자열 → 종류. 알 수 없는 값은 예외(닫힌 집합) — 역직렬화 경계용. */
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityPoiType = entries.first { it.wire == value }
    }
}

/**
 * 경로상 POI 1건. 프론트 MapMock POI(`{ type, x, y }`)와 호환되도록 정규화 좌표 x/y(0~100)를 주고,
 * 실지도 연동을 위해 lat/lng 도 함께 준다(프론트는 필요한 쪽만 사용).
 *
 * safehouse(여성안심지킴이집·B-2)는 지정 거점일 뿐 영업시간 정보가 없어, 프론트에서 별도 주의 문구로 안내한다.
 */
data class FacilityPoi(
    val type: FacilityPoiType,
    val x: Int,
    val y: Int,
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    /** CCTV 마커 표시용 설치목적구분(용도)·카메라대수. 다른 유형/데이터 결측 시 null. */
    val purpose: String? = null,
    val cameraCount: Int? = null,
    /** 소재지 주소(마커 클릭 정보 카드 표시용). 데이터 결측 시 null. */
    val address: String? = null,
    /** 전화번호(지구대·파출소 등 — 정보 카드에서 전화 연결). 데이터 결측 시 null. */
    val phone: String? = null,
)

/**
 * 안심시설 요약. 프론트 SafetyFacilitySummary 와 동일 필드. pois 를 실제로 집계한 값이라
 * 화면에 렌더된 POI 와 항상 일치한다(없는 데이터를 지어내지 않음).
 */
data class FacilitySummary(
    val cctv: Int,
    val bell: Int,
    val store: Int,
    val police: Int,
    /** 여성안심지킴이집(B-2) 수. 지정 거점 — 영업시간 보장이 아님. */
    val safehouse: Int,
    /** 안심시설 합계(start/end 제외). */
    val total: Int,
)

/** POST /api/routes/facilities 응답: 지도 표시용 POI 목록 + 집계 요약. */
data class FacilitiesResponse(
    val pois: List<FacilityPoi>,
    val summary: FacilitySummary,
)
