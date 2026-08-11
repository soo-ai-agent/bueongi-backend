package io.soo.bueongi.client.tmap

/**
 * Tmap 보행자 경로 API 호출 규격 — 엔드포인트·탐색 옵션을 한곳에 모은다.
 * 값이 바뀌면(버전 상향, 옵션 조정) 이 파일만 고친다.
 */
object TmapApiSpec {

    const val PEDESTRIAN_ENDPOINT: String = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"

    /** Tmap searchOption 규격: 추천=0, 대로우선=4, 최단=10. 세 갈래를 받아 비교 후보로 쓴다. */
    val DEFAULT_SEARCH_OPTIONS: List<String> = listOf("0", "4", "10")

    /** 경유지 경로는 추천(0) 하나만 쓴다 — 경유지가 이미 경로를 규정하기 때문. */
    const val VIA_SEARCH_OPTION: String = "0"
}
