package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** [wire] 가 와이어 계약([JsonValue]) — lastRefreshStatus 가 정확히 replaced|kept|never|skipped 를 내야 한다. */
enum class FacilityRefreshStatus(@get:JsonValue val wire: String) {
    /** 예약값 — 백엔드가 외부 API 를 직접 부르던 시절의 상태다. 현재는 발생하지 않는다. */
    NEVER("never"),

    /** 관리자 CSV 업로드로 교체됨. */
    REPLACED("replaced"),

    /** 교체 시도가 있었으나 반영하지 않음(기존 데이터 유지). */
    KEPT("kept"),

    /** 교체 이력 없음 — 파이프라인이 만든 CDN 파일 그대로 쓰는 정상 상태다. */
    SKIPPED("skipped"),
    ;

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityRefreshStatus = entries.first { it.wire == value }
    }
}
