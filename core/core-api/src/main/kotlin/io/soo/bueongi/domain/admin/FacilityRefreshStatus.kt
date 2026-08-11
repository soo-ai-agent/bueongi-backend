package io.soo.bueongi.domain.admin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** [wire] 가 와이어 계약([JsonValue]) — lastRefreshStatus 가 정확히 replaced|kept|never|skipped 를 내야 한다. */
enum class FacilityRefreshStatus(@get:JsonValue val wire: String) {
    /** 라이브 원천이 있는데 아직 한 번도 갱신되지 않음 — 점검 대상. */
    NEVER("never"),
    REPLACED("replaced"),
    KEPT("kept"),

    /** 라이브 원천이 없거나 키 미설정이라 갱신 대상이 아님 — 고장이 아니다. */
    SKIPPED("skipped"),
    ;

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityRefreshStatus = entries.first { it.wire == value }
    }
}
