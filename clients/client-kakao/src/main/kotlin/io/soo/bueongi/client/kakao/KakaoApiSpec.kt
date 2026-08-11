package io.soo.bueongi.client.kakao

/**
 * Kakao Local API 호출 규격 — 엔드포인트·페이지 크기를 한곳에 모은다.
 * 값이 바뀌면(경로 변경, 결과 수 조정) 이 파일만 고친다.
 */
object KakaoApiSpec {

    const val BASE_URL: String = "https://dapi.kakao.com"

    const val KEYWORD_SEARCH_PATH: String = "/v2/local/search/keyword.json"

    /** 키워드 검색 1회 결과 수. Kakao Local 허용 범위는 1~15 다. */
    const val SEARCH_SIZE: Int = 15
}
