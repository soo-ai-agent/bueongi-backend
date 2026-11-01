package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

// 보호자·관리자 위치 응답의 status 칸.
enum class ShareProgressStatus(
    @get:JsonValue val wire: String,
) {
    IN_PROGRESS("in_progress"),

    // 예정 초과 + 위치 정지인데 마지막 위치가 목적지 근처 — 도착하고 공유만 안 끝낸 것으로 본다.
    ARRIVAL_LIKELY("arrival_likely"),

    MISSED_ARRIVAL_SUSPECT("missed_arrival_suspect"),
}
