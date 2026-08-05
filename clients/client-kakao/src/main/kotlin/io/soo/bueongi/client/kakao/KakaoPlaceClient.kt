package io.soo.bueongi.client.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Kakao Local 키워드 검색 클라이언트(자립 모듈). `bueongi.places.kakao-rest-api-key`
 * (환경변수 KAKAO_REST_API_KEY, client-kakao.yml)가 설정되면 [isEnabled]=true.
 *
 * 반환 모델은 이 모듈 소유의 [KakaoPlace](경계 검증 통과분만) — 도메인 계약(PlaceItem) 매핑은
 * 상위(core-api 의 KakaoRemotePlaceProvider)가 한다. RestClient.Builder 는 Spring Boot 가
 * 자동구성한 빈(앱 ObjectMapper·메시지 컨버터 상속)을 주입받아 사용한다.
 */
@Component
class KakaoPlaceClient(
    @Value("\${bueongi.places.kakao-rest-api-key:}") apiKey: String,
    restClientBuilder: RestClient.Builder,
) {

    private val apiKey: String = apiKey.trim()

    /** REST 키가 설정되어 실검색이 가능한지. false 면 호출하지 말 것(상위가 시드로 폴백). */
    val isEnabled: Boolean
        get() = apiKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone()
        .baseUrl(KAKAO_BASE_URL)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK ${this.apiKey}")
        .build()

    /** 키워드 검색. 경계 검증(이름/주소/좌표 유효성)을 통과한 장소만 반환한다. */
    fun search(keyword: String): List<KakaoPlace> {
        check(isEnabled) { "Kakao REST API key 미설정 — isEnabled 확인 후 호출해야 한다." }
        val response: KakaoKeywordResponse? = restClient.get()
            .uri { builder ->
                builder.path(KEYWORD_SEARCH_PATH)
                    .queryParam("query", keyword)
                    .queryParam("size", DEFAULT_SIZE)
                    .build()
            }
            .retrieve()
            .body(KakaoKeywordResponse::class.java)
        return response?.documents.orEmpty().mapNotNull { it.toKakaoPlace() }
    }

    companion object {
        private const val KAKAO_BASE_URL: String = "https://dapi.kakao.com"
        private const val KEYWORD_SEARCH_PATH: String = "/v2/local/search/keyword.json"
        private const val DEFAULT_SIZE: Int = 15
    }
}
