package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

// wire 가 와이어 계약(JsonValue) — lastRefreshStatus 가 정확히 replaced|kept|never|skipped 를 내야 한다.
enum class SafetySpotRefreshStatus(@get:JsonValue val wire: String) {
    // 예약값 — 현재 파이프라인에서는 발생하지 않는다.
    NEVER("never"),

    REPLACED("replaced"),

    KEPT("kept"),

    SKIPPED("skipped"),
}
