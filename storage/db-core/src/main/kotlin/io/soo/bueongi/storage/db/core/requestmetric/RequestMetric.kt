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
