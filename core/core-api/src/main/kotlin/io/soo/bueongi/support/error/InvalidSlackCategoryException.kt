package io.soo.bueongi.support.error

/**
 * 관리자 Slack 설정 편집(PUT /api/admin/slack-config)에서 뮤트 카테고리 key 가 알려진 5종 밖일 때 던진다.
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 422 `code:"INVALID_SLACK_CATEGORY"` 로 변환한다 — 형식(문자열 목록)은
 * 읽혔으나 값 규약(허용 카테고리)을 못 지킨 복구 가능 상태다(INVALID_ROUTE_META 와 같은 경계-검증→422 결).
 */
class InvalidSlackCategoryException(
    /** 거부된(알 수 없는) 카테고리 key 목록(정렬). 응답 errors.rejectedValue 로 전달한다. */
    val rejectedValue: List<String>,
    /** 허용된 카테고리 key 전체. 메시지에 안내한다. */
    val knownKeys: List<String>,
) : RuntimeException(
    "알 수 없는 알림 카테고리입니다: ${rejectedValue.joinToString(", ")} (허용: ${knownKeys.joinToString(", ")})",
)
