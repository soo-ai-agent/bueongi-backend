package io.soo.bueongi.client.datagokr

/**
 * data.go.kr 공통 호출 규격 — 페이징·재시도·쿼터. 엔드포인트는 자원별 클라이언트 모듈이 갖는다.
 * 값이 바뀌면(쿼터 상향 등) 이 파일만 고친다.
 */
object DataGoKrApiSpec {





    /** 페이지당 최대 건수. numOfRows 를 더 크게 줘도 서버가 100 으로 고정한다(실측). */
    const val PAGE_SIZE: Int = 100

    /** 전량 수집 페이지 상한 — 무한 루프 방지용 안전장치. */
    const val MAX_PAGES: Int = 5000

    /** 한 페이지당 시도 횟수(최초 1 + 재시도 2). 대량 수집 중 간헐 503(SERVICETIMEOUT)이 난다. */
    const val MAX_ATTEMPTS_PER_PAGE: Int = 3

    /** 재시도 백오프 기본 간격 — 시도 횟수에 비례해 늘린다(1초, 2초). */
    const val RETRY_BASE_DELAY_MS: Long = 1000L

    /** 일일 호출 쿼터. 어떤 종류를 라이브 갱신할지 정하는 기준이다(초과 시 503 유발). */
    const val DAILY_QUOTA: Int = 1000

    /** 응답 header.resultCode 중 성공으로 보는 값. */
    val SUCCESS_CODES: Set<String> = setOf("0", "00")
}
