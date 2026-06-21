package io.soo.bueongi.places

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Kakao Local 키워드 검색(실연동) 소스. `bueongi.places.kakao-rest-api-key`(환경변수 KAKAO_REST_API_KEY)가
 * 설정되면 [isEnabled]=true 가 되어 실제 Kakao Local API 로 검색하고, 미설정이면 [PlaceSearchService] 가 시드로 폴백한다.
 *
 * 응답을 [PlaceItem] 계약으로 매핑하므로 프론트는 무키/유키 어느 경로든 동일 shape 를 받는다(계약 불변).
 * RestClient.Builder 는 Spring Boot 가 자동구성한 빈(앱 ObjectMapper·메시지 컨버터 상속)을 주입받아 사용한다.
 */
@Component
class KakaoPlaceProvider(
    @Value("\${bueongi.places.kakao-rest-api-key:}") apiKey: String,
    restClientBuilder: RestClient.Builder,
) : RemotePlaceProvider {

    private val apiKey: String = apiKey.trim()

    override val isEnabled: Boolean
        get() = apiKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone()
        .baseUrl(KAKAO_BASE_URL)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK ${this.apiKey}")
        .build()

    override fun search(keyword: String): List<PlaceItem> {
        check(isEnabled) { "Kakao REST API key 미설정 — isEnabled 확인 후 호출해야 한다." }
        val response = restClient.get()
            .uri { builder ->
                builder.path(KEYWORD_SEARCH_PATH)
                    .queryParam("query", keyword)
                    .queryParam("size", DEFAULT_SIZE)
                    .build()
            }
            .retrieve()
            .body(KakaoKeywordResponse::class.java)
        return response?.documents.orEmpty().mapNotNull { it.toPlaceItem() }
    }

    companion object {
        private const val KAKAO_BASE_URL = "https://dapi.kakao.com"
        private const val KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json"
        private const val DEFAULT_SIZE = 15
    }
}

/** Kakao Local 키워드 검색 응답 래퍼. meta 등 미사용 필드는 무시한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoKeywordResponse(
    val documents: List<KakaoPlaceDocument> = emptyList(),
)

/**
 * Kakao Local 장소 1건. 도로명주소(road_address_name)를 우선하고 없으면 지번주소(address_name)로 폴백한다.
 * 좌표 x=경도(lng), y=위도(lat)는 문자열로 오므로 Double 로 파싱한다 — 필수 필드 누락/파싱 실패 건은 [toPlaceItem] 에서 제외.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoPlaceDocument(
    @JsonProperty("place_name") val placeName: String? = null,
    @JsonProperty("road_address_name") val roadAddressName: String? = null,
    @JsonProperty("address_name") val addressName: String? = null,
    @JsonProperty("x") val lng: String? = null,
    @JsonProperty("y") val lat: String? = null,
) {
    /**
     * [PlaceItem] 으로 매핑. 이름/주소/좌표 중 하나라도 없거나 좌표 파싱 실패면 null(결과에서 제외).
     *
     * 좌표는 파싱 성공만으로 충분치 않다 — `"NaN".toDoubleOrNull()`=NaN, `"Infinity"`=∞, 범위를 벗어난 값도
     * 모두 파싱은 통과한다. 이런 비유효 좌표가 [PlaceItem] 으로 새어 프론트→`RouteRequest.destination` 으로
     * 흘러가면, haversine 이 NaN 거리를 내고 거리 가드(`<`/`>` 비교는 NaN 에 대해 항상 false)를 통과해
     * 무의미한 경로/시설을 만든다. 외부 데이터 경계에서 "파싱 실패 제외"와 같은 정책으로 비유효 좌표도 제외한다.
     */
    fun toPlaceItem(): PlaceItem? {
        val name = placeName?.takeIf { it.isNotBlank() } ?: return null
        val address = roadAddressName?.takeIf { it.isNotBlank() }
            ?: addressName?.takeIf { it.isNotBlank() }
            ?: return null
        val latValue = lat?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val lngValue = lng?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        return PlaceItem(name = name, address = address, lat = latValue, lng = lngValue)
    }
}
