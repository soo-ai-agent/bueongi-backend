package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.client.tmap.TmapPlace
import io.soo.bueongi.client.tmap.TmapPlaceClient
import io.soo.bueongi.core.place.api.response.PlaceResponse
import io.soo.bueongi.core.place.domain.error.PlaceSearchUnavailableException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// Tmap 을 아는 마지막 자리 — 장소 공급자를 바꾸면 clients 모듈과 이 파일만 바뀐다.
@Service
class PlaceSearchService(
    private val tmapPlaceClient: TmapPlaceClient,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    fun search(keyword: String): List<PlaceResponse> {
        val tmapPlaces: List<TmapPlace> =
            try {
                tmapPlaceClient.search(keyword)
            } catch (e: Exception) {
                log.warn("외부 장소 검색 실패 — 503 으로 알림 | 원인=${e.javaClass.simpleName}")
                throw PlaceSearchUnavailableException(cause = e)
            }
        val places: ArrayList<PlaceResponse> = ArrayList(tmapPlaces.size)
        for (tmapPlace in tmapPlaces) {
            places.add(tmapPlace.toPlaceResponse())
        }
        return places
    }
}

private fun TmapPlace.toPlaceResponse(): PlaceResponse {
    return PlaceResponse(
        name = name,
        address = address,
        lat = lat,
        lng = lng,
    )
}
