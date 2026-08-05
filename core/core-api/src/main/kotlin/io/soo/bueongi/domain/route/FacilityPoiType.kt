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
