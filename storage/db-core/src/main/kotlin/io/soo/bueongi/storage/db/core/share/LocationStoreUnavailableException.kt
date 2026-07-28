package io.soo.bueongi.storage.db.core.share

/**
 * 공유 저장소 백엔드에 접근할 수 없을 때 던지는 [LocationStore] 계약 예외.
 *
 * [RedisLocationStore] 가 Redis 연결 실패·타임아웃(Spring Data `DataAccessException`)을 이 예외로 변환한다.
 * "없음"([LocationStore.findByToken] = null, 만료/미존재)과 명확히 구분되는 **인프라 장애 신호**다 —
 * 상위(API)는 이를 503 으로 변환해 "일시적으로 위치 공유 서버에 연결할 수 없음"을 정직하게 알린다
 * (위치가 갱신·조회된 척하지 않는다). [InMemoryLocationStore] 는 인메모리라 이 예외를 던지지 않는다.
 *
 * @param operation 실패한 저장소 연산 이름(로깅·진단용). 토큰 등 식별 정보는 담지 않는다.
 */
class LocationStoreUnavailableException(
    operation: String,
    cause: Throwable,
) : RuntimeException("위치 공유 저장소 접근 실패(operation=$operation)", cause)
