package io.soo.bueongi.client.kakao

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class KakaoPlaceClient(
    @Value("\${bueongi.places.kakao-rest-api-key:}") apiKey: String,
    restClientBuilder: RestClient.Builder,
) {

    private val apiKey: String = apiKey.trim()

    val isEnabled: Boolean
        get() = apiKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone()
        .baseUrl(KakaoApiSpec.BASE_URL)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK ${this.apiKey}")
        .build()

    fun search(keyword: String): List<KakaoPlace> {
        check(isEnabled) { "Kakao REST API key 미설정 — isEnabled 확인 후 호출해야 한다." }
        val response: KakaoKeywordResponse? = restClient.get()
            .uri { builder ->
                builder.path(KakaoApiSpec.KEYWORD_SEARCH_PATH)
                    .queryParam("query", keyword)
                    .queryParam("size", KakaoApiSpec.SEARCH_SIZE)
                    .build()
            }
            .retrieve()
            .body<KakaoKeywordResponse>()
        return response?.documents.orEmpty().mapNotNull { it.toKakaoPlace() }
    }
}
