package io.soo.bueongi.storage.db.core.requestmetric

/**
 * 수신(incoming) 요청 1건의 계측 기록. 관리자 페이지의 "트래픽" 화면에 집계되어 노출된다 —
 * 언제([atEpochMs]) 어떤 메서드·경로([method]·[endpoint])의 요청이, 어떤 익명 클라이언트([clientId])로부터,
 * 어떤 응답 상태([status])로 얼마나 걸려([durationMs]) 처리됐는지.
 *
 * [endpoint] 는 **정규화된 경로 템플릿**이다(동적 토큰 세그먼트는 `{token}` 으로 치환 — 카디널리티 폭발 방지).
 * PII 는 담지 않는다 — 좌표·검색어·토큰 원문은 넣지 않고, [clientId] 는 프론트가 부여한 **익명 식별자**(`X-Client-Id`)만이다.
 */
data class RequestMetric(
    /** 요청 시작 시각(epoch ms). 최근순 정렬·시간대/일자 집계용. */
    val atEpochMs: Long,
    /** HTTP 메서드: GET | POST | … (엔드포인트 집계 키의 일부). */
    val method: String,
    /** 정규화된 경로 템플릿: 예 `/share/{token}/location`, `/api/places/search`. */
    val endpoint: String,
    /** 익명 클라이언트 식별자(`X-Client-Id` 헤더). 없으면 null — 사용자 추정에서 제외되나 호출수엔 포함된다. */
    val clientId: String?,
    /** 응답 HTTP status(2xx·3xx 를 성공으로 본다). */
    val status: Int,
    /** 처리 소요시간(ms). */
    val durationMs: Long,
)

/**
 * 수신 요청 메트릭 저장소. 외부 API 접속 내역([io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore])·
 * 추천 통계([io.soo.bueongi.storage.db.core.routestats.RouteStatsStore])와 동일 철학 —
 * Redis 주입 시 [RedisRequestMetricStore], 미주입(로컬/CI/MVP)이면 [InMemoryRequestMetricStore] 가 동일 계약으로 폴백한다.
 *
 * 최근 [REQUEST_METRIC_CAP] 건만 유지하는 순환 버퍼다(오래된 것은 축출). 기록은 요청 처리의 순수 부수효과이므로,
 * [record] 는 어떤 경우에도 예외를 밖으로 던지지 않는다(계측 실패가 실제 요청을 깨면 안 된다 — 내부에서 삼키고 로깅만).
 */
interface RequestMetricStore {
    /** 계측 1건 저장. **절대 예외를 던지지 않는다**(내부 try/catch 로 삼킨다). */
    fun record(metric: RequestMetric)

    /** 최근순(최신 먼저) 조회. 최대 [limit] 건. */
    fun findRecent(limit: Int): List<RequestMetric>
}

/** 순환 버퍼 상한 — 최근 이만큼만 유지한다(Redis LTRIM / 인메모리 deque 축출). 전량 수신 트래픽이라 넉넉히 잡는다. */
const val REQUEST_METRIC_CAP: Int = 5000
