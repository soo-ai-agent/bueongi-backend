package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.core.place.domain.model.PlaceItem

import io.soo.bueongi.core.place.domain.error.InvalidKeywordException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PlaceSearchService(
    private val seedProvider: SeedPlaceProvider,
    private val remoteProvider: RemotePlaceProvider,
    private val placeEvents: PlaceEventPublisher? = null,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    fun search(keyword: String?): List<PlaceItem> {
        val trimmed: String = keyword?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        if (trimmed.length > MAX_KEYWORD_LENGTH) {
            throw InvalidKeywordException(maxLength = MAX_KEYWORD_LENGTH, actualLength = trimmed.length)
        }
        if (!remoteProvider.isEnabled) {
            return seedProvider.search(trimmed)
        }
        return try {
            remoteProvider.search(trimmed)
        } catch (e: Exception) {
            log.warn("외부 장소 검색 실패 → 시드 폴백 (keyword='{}'): {}", trimmed, e.message)
            placeEvents?.placeSearchFellBack(
                "${e.javaClass.simpleName}: ${e.message ?: "메시지 없음"} — 시드 카탈로그로 폴백 중",
            )
            seedProvider.search(trimmed)
        }
    }

    companion object {
        const val MAX_KEYWORD_LENGTH: Int = 100
    }
}
