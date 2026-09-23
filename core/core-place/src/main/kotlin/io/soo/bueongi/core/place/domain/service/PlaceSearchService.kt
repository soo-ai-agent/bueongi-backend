package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.client.kakao.KakaoPlace
import io.soo.bueongi.client.kakao.KakaoPlaceClient
import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.core.place.api.response.PlaceResponse
import io.soo.bueongi.core.place.domain.error.PlaceSearchUnavailableException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class PlaceSearchService(
    private val kakaoPlaceClient: KakaoPlaceClient,
    private val events: ApplicationEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    fun search(keyword: String): List<PlaceResponse> {
        try {
            val places: List<KakaoPlace> = kakaoPlaceClient.search(keyword)
            val responses: ArrayList<PlaceResponse> = ArrayList(places.size)
            for (place in places) {
                responses.add(PlaceResponse.from(place))
            }
            return responses
        } catch (e: Exception) {
            val cause: String = e.javaClass.simpleName
            log.warn("외부 장소 검색 실패 — 503 으로 알림 | 원인=$cause")
            events.publishEvent(OperationEvent(key = "kakao-place-failed", title = "Kakao 장소검색 실패", detail = cause))
            throw PlaceSearchUnavailableException(cause = e)
        }
    }
}
