package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.core.place.domain.model.PlaceItem

import io.soo.bueongi.core.place.domain.error.InvalidKeywordException
import io.soo.bueongi.support.logging.ExceptionLogLine
import io.soo.bueongi.support.logging.IGNORED_EXCEPTION
import io.soo.bueongi.support.logging.causeChainOf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PlaceSearchService(
    private val seedProvider: SeedPlaceProvider,
    private val remoteProvider: RemotePlaceProvider,
    // 알림 없이도 검색은 돌아야 해서 선택 주입이다.
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
            // 검색어는 남기지 않는다 — 사용자가 어디로 가려 했는지가 로그에 쌓인다.
            val cause: String = causeChainOf(e)
            log.warn(
                ExceptionLogLine(
                    kind = IGNORED_EXCEPTION,
                    counterpart = "장소 검색",
                    subject = "외부 장소 검색",
                    cause = cause,
                    action = "시드 카탈로그로 폴백",
                ).format(),
            )
            placeEvents?.placeSearchFellBack("$cause — 시드 카탈로그로 폴백 중")
            seedProvider.search(trimmed)
        }
    }

    companion object {
        const val MAX_KEYWORD_LENGTH: Int = 100
    }
}
