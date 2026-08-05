package io.soo.bueongi.domain.facility

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.core.enums.FacilityType

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
