package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.client.place.Place
import io.soo.bueongi.client.place.PlaceSearchClient
import io.soo.bueongi.core.place.api.response.PlaceResponse
import io.soo.bueongi.core.place.domain.error.PlaceSearchUnavailableException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PlaceSearchService(
    private val placeSearchClient: PlaceSearchClient,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    fun search(keyword: String): List<PlaceResponse> {
        val places: List<Place> =
            try {
                placeSearchClient.search(keyword)
            } catch (e: Exception) {
                log.warn("외부 장소 검색 실패 — 503 으로 알림 | 원인=${e.javaClass.simpleName}")
                throw PlaceSearchUnavailableException(cause = e)
            }
        return PlaceResponse.from(places)
    }
}
