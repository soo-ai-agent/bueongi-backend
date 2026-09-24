package io.soo.bueongi.client.tmap

import io.soo.bueongi.client.tmap.response.TmapPoiSearch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

// 장소(POI) 통합 검색: https://tmapapi.tmapmobility.com/main.html#webservice/docs/tmapPoiSearch
@Component
class TmapPlaceClient(
    @Value("\${bueongi.tmap.app-key:}") appKey: String,
    restClientBuilder: RestClient.Builder,
) {
    private val appKey: String = appKey.trim()

    val isEnabled: Boolean
        get() = appKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone().build()

    fun search(keyword: String): List<TmapPlace> {
        check(isEnabled) { "Tmap app key 미설정 — isEnabled 확인 후 호출해야 한다." }
        val response: TmapPoiSearch? =
            restClient.get()
                .uri(POI_SEARCH_ENDPOINT) { builder ->
                    builder.queryParam("version", 1)
                        .queryParam("searchKeyword", keyword)
                        .queryParam("count", SEARCH_COUNT)
                        .queryParam("reqCoordType", "WGS84GEO")
                        .queryParam("resCoordType", "WGS84GEO")
                        .build()
                }
                .header("appKey", appKey)
                .retrieve()
                .body<TmapPoiSearch>()
        val pois = response?.searchPoiInfo?.pois?.poi.orEmpty()
        val places: ArrayList<TmapPlace> = ArrayList(pois.size)
        for (poi in pois) {
            val place: TmapPlace? = poi.toTmapPlace()
            if (place == null) {
                continue
            }
            places.add(place)
        }
        return places
    }

    private companion object {
        const val POI_SEARCH_ENDPOINT: String = "https://apis.openapi.sk.com/tmap/pois"

        // 화면이 쓰는 만큼만 받는다 — 사용자가 보는 것은 상위 몇 건이다.
        const val SEARCH_COUNT: Int = 15
    }
}
