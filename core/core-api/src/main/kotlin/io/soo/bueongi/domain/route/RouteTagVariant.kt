package io.soo.bueongi.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.facility.GeoPoint

/**
 * 경로 표시 태그 variant — 프론트 Badge variant 어휘. [wire] 문자열('default'|'mint'|'blue'|'yellow'|'outline')이
 * 곧 응답 계약이라(프론트가 variant→아이콘으로 매핑) [JsonValue] 로 못 박고, 역직렬화는 [from] 으로 고정한다([JsonCreator]).
 */
enum class RouteTagVariant(@get:JsonValue val wire: String) {
    DEFAULT("default"),
    MINT("mint"),
    BLUE("blue"),
    YELLOW("yellow"),
    OUTLINE("outline"),
    ;

    companion object {
        /** variant 문자열 → 값. 알 수 없는 값은 예외(닫힌 집합) — 역직렬화 경계용. */
        @JsonCreator
        @JvmStatic
        fun from(value: String): RouteTagVariant = entries.first { it.wire == value }
    }
}
