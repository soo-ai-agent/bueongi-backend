package io.soo.bueongi.support.error

/**
 * 위치 공유 토큰이 없거나 만료된 상태에서 위치 갱신(POST /share/{token}/location)을 시도할 때 던진다.
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 404 `code:"SHARE_SESSION_NOT_FOUND"` 로 변환한다. 미매칭 경로(NOT_FOUND)와 달리
 * "경로는 유효하나 그 공유 리소스가 없다"는 도메인 상태다. (보호자 조회는 404 대신 `expired:true` 로 응답.)
 */
class ShareSessionNotFoundException : RuntimeException("공유를 찾을 수 없거나 만료되었습니다.")
