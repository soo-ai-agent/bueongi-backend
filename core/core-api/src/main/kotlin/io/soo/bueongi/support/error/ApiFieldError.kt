package io.soo.bueongi.support.error

/** 표준 에러 스키마의 필드별 항목. 프론트가 좌표 입력 폼의 어떤 필드를 어떻게 고쳐야 하는지 안내한다. */
data class ApiFieldError(
    /** 거부된 필드 경로. 중첩 시 `origin.lat`, `destination.lng` 형태. */
    val field: String,
    /** 사람이 읽을 수 있는 사유(검증 메시지). */
    val message: String,
    /** 거부된 실제 값(null 가능). */
    val rejectedValue: Any?,
)
