package io.soo.bueongi.storage.db.core.share.store

// 상위가 503 으로 옮긴다 — 메시지에 토큰 등 식별 정보 금지.
class LocationStoreUnavailableException(operation: String, cause: Throwable) : RuntimeException(
    "위치 공유 저장소 접근 실패(operation=$operation)",
    cause,
)
