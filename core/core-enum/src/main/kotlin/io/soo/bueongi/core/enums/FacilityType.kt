package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 안심시설 종류 — 전 계층(storage 적재·domain 점수·api 마커)이 공유하는 도메인 어휘.
 * CDN 컬렉션(type)과 매핑된다:
 * cctv=B-1, lamp(보안등)=C-2, bell(비상벨)=D-1/D-2, safehouse(여성안심지킴이집)=B-2, police=B-3/B-4.
 *
 * [cdnType] 이 와이어 문자열이다([JsonValue]) — 관리자 업로드/현황 응답의 type 필드가 이 값이라
 * 직렬화가 정확히 cctv|lamp|emergency_bell|safehouse|police 를 내야 한다. 역직렬화는 [from] 으로 고정한다([JsonCreator]).
 */
enum class FacilityType(@get:JsonValue val cdnType: String) {
    CCTV("cctv"),
    LAMP("lamp"),
    BELL("emergency_bell"),
    SAFEHOUSE("safehouse"),
    POLICE("police"),
    ;

    companion object {
        /** cdnType 문자열 → 종류. 알 수 없는 값은 예외(닫힌 집합) — 역직렬화 경계용. */
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityType = entries.first { it.cdnType == value }
    }
}
