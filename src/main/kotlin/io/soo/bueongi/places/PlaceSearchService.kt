package io.soo.bueongi.places

import io.soo.bueongi.common.InvalidKeywordException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 장소 검색 서비스. 검색어 트림/빈값 가드를 책임지고, 소스 선택([RemotePlaceProvider] ↔ [SeedPlaceProvider])을 조정한다.
 *
 * - 외부 키 미설정([RemotePlaceProvider.isEnabled]=false) → 시드 카탈로그 검색.
 * - 외부 키 설정 → 실연동(Kakao Local) 검색. **실패(예외) 시 시드로 폴백** — 안심 귀가 앱에서 검색 불능보다 근사 결과 제공이 안전.
 *
 * 어느 경로든 응답은 [PlaceItem] 계약으로 동일하므로 프론트는 변경이 필요 없다.
 */
@Service
class PlaceSearchService(
    private val seedProvider: SeedPlaceProvider,
    private val remoteProvider: RemotePlaceProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 검색어로 장소를 찾는다. 빈/공백 검색어는 빈 목록(프론트가 '최근 검색'으로 폴백) — 추측 결과를 만들지 않는다.
     * 앞뒤 공백 트림 후, 키가 있으면 실연동을 시도하고 실패하면 시드로 폴백한다.
     *
     * 정책 경계: 트림 후 **빈 검색어 → 빈 목록(에러 아님)**, **최대 길이 초과 → [InvalidKeywordException](400)**.
     * 길이 가드는 외부 API([RemotePlaceProvider]) 호출 전에 적용해 불필요한 외부 호출/남용을 차단한다.
     */
    fun search(keyword: String?): List<PlaceItem> {
        val trimmed = keyword?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length > MAX_KEYWORD_LENGTH) {
            throw InvalidKeywordException(maxLength = MAX_KEYWORD_LENGTH, actualLength = trimmed.length)
        }
        if (!remoteProvider.isEnabled) return seedProvider.search(trimmed)
        return try {
            remoteProvider.search(trimmed)
        } catch (e: Exception) {
            log.warn("외부 장소 검색 실패 → 시드 폴백 (keyword='{}'): {}", trimmed, e.message)
            seedProvider.search(trimmed)
        }
    }

    companion object {
        /**
         * 검색어(트림 후) 최대 길이. 장소명/주소 일부로는 충분히 넉넉하고, 이를 넘는 입력은 사실상
         * 오작동/남용이므로 외부 API 호출 전에 400 으로 차단한다.
         */
        const val MAX_KEYWORD_LENGTH = 100
    }
}
