package io.soo.bueongi.client.safemap

/**
 * 생활안전지도(safemap) 치안시설 API 호출 규격.
 * 값이 바뀌면(엔드포인트 이전, 페이지 상한 조정) 이 파일만 고친다.
 */
object SafemapApiSpec {

    const val ENDPOINT: String = "https://safemap.go.kr/openapi2/IF_0036"

    const val PAGE_SIZE: Int = 1000

    /** 전량 수집 페이지 상한 — 무한 루프 방지용 안전장치. */
    const val MAX_PAGES: Int = 500
}
