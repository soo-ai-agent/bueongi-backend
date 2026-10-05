package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

// apiType 이 와이어 계약(JsonValue) — RouteOption.type 이 정확히 'safe'|'main'|'fast' 를 내야 하는 프론트 계약.
// requestId 는 프론트가 골라 보내는 번호 — 응답의 RouteOption.id 와 같은 값이다.
enum class RouteType(
    @get:JsonValue val apiType: String,
    val requestId: String,
) {
    SAFE("safe", "1"),
    MAIN("main", "2"),
    FAST("fast", "3"),
    ;

    companion object {
        fun of(value: String?): RouteType {
            if (value == null) {
                return SAFE
            }
            val normalized: String = value.trim().lowercase()
            for (type in entries) {
                if (type.apiType == normalized || type.requestId == normalized) {
                    return type
                }
            }
            return SAFE
        }
    }
}
