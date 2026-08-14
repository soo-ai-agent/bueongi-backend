package io.soo.bueongi.place

import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import io.soo.bueongi.storage.db.core.apilog.measure
import io.soo.bueongi.support.error.InvalidKeywordException
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PlaceSearchService(
    private val seedProvider: SeedPlaceProvider,
    private val remoteProvider: RemotePlaceProvider,
    private val alertNotifier: AlertNotifier? = null,
    private val apiCallLogStore: ApiCallLogStore? = null,
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
            // ponytail: service 레벨 계측 — statusCode/resultCode 등 client 내부 상세는 후속. client 모듈 계측 훅(nullable recorder 주입)으로 승격 가능
            apiCallLogStore.measure(provider = ApiProvider.KAKAO, endpoint = "search", success = { true }) {
                remoteProvider.search(trimmed)
            }
        } catch (e: Exception) {
            log.warn("외부 장소 검색 실패 → 시드 폴백 (keyword='{}'): {}", trimmed, e.message)
            // 검색어는 사용자 입력이라 알림에 싣지 않는다(개인정보 최소화) — 원인만.
            alertNotifier?.notify(
                key = "kakao-place-fallback",
                title = "Kakao 장소검색 실패",
                detail = "${e.javaClass.simpleName}: ${e.message ?: "메시지 없음"} — 시드 카탈로그로 폴백 중",
            )
            seedProvider.search(trimmed)
        }
    }

    companion object {
        const val MAX_KEYWORD_LENGTH: Int = 100
    }
}
