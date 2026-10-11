package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

// wire 가 와이어 계약(JsonValue) — 관리자 알림 이력의 severity 가 정확히 critical|degraded|unknown 을 내야 한다.
enum class AlertSeverity(
    @get:JsonValue val wire: String,
) {
    CRITICAL("critical"),

    DEGRADED("degraded"),

    // AlertCategory 로 만들지 않은 key 가 이력에 들어온 경우.
    UNKNOWN("unknown"),
}
