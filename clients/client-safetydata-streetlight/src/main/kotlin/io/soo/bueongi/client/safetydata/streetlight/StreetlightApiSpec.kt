package io.soo.bueongi.client.safetydata

/**
 * 재난안전데이터 공유플랫폼(safetydata) 가로등 API(DSSP-IF-00084) 호출 규격.
 * data.go.kr 과 다른 키·쿼터를 쓰므로 상수도 분리해 둔다.
 */
object SafetydataApiSpec {

    const val DEFAULT_BASE_URL: String = "https://www.safetydata.go.kr"

    const val PATH: String = "/V2/api/DSSP-IF-00084"

    const val ENDPOINT: String = "$DEFAULT_BASE_URL$PATH"

    /** 페이지당 최대 건수 — data.go.kr(100) 과 달리 1000 을 받아 호출 수가 훨씬 적다. */
    const val PAGE_SIZE: Int = 1000

    /** 전량 수집 페이지 상한 — 무한 루프 방지용 안전장치. */
    const val MAX_PAGES: Int = 1000
}
