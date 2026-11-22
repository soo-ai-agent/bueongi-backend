package io.soo.bueongi.client.tmap.place

import com.fasterxml.jackson.databind.JsonNode
import io.soo.bueongi.client.place.Place
import io.soo.bueongi.client.place.PlaceSearchClient
import io.soo.bueongi.client.tmap.place.query.buildPoiSearchUri
import io.soo.bueongi.client.tmap.place.response.parseTmapPoiSearch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

// 장소(POI) 통합 검색: https://tmapapi.tmapmobility.com/main.html#webservice/docs/tmapPoiSearch
@Component
class TmapPlaceClient(
    @Value("\${bueongi.tmap.app-key:}") appKey: String,
    restClientBuilder: RestClient.Builder,
) : PlaceSearchClient {
    private val appKey: String = appKey.trim()
    private val restClient: RestClient = restClientBuilder.clone().build()

    override fun search(keyword: String): List<Place> {
        val response: JsonNode =
            restClient.get()
                .uri(POI_SEARCH_ENDPOINT) { builder -> buildPoiSearchUri(builder, keyword) }
                .header("appKey", appKey)
                .retrieve()
                .body<JsonNode>() ?: return emptyList()
        return parseTmapPoiSearch(response)
    }

    private companion object {
        const val POI_SEARCH_ENDPOINT: String = "https://apis.openapi.sk.com/tmap/pois"
    }
}
