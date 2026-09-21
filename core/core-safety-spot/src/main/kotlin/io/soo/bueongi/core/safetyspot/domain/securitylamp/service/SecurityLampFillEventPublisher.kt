package io.soo.bueongi.core.safetyspot.domain.securitylamp.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class SecurityLampFillEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun regionFetchFailed(regionCode: String) {
        events.publishEvent(
            OperationEvent(
                key = FETCH_FAILED_KEY,
                title = FETCH_FAILED_TITLE,
                detail = "지역 $regionCode 보안등 수집 실패 — 기존 데이터 유지, 다음 요청에서 재시도",
            ),
        )
    }

    fun regionHadNoCoordinates(
        regionCode: String,
        fetched: Int,
    ) {
        events.publishEvent(
            OperationEvent(
                key = NO_COORDINATES_KEY,
                title = NO_COORDINATES_TITLE,
                detail = "지역 $regionCode 보안등 ${fetched}건을 받았으나 좌표가 없어 적재 0건 — 지오코딩 필요",
            ),
        )
    }

    companion object {
        private const val FETCH_FAILED_KEY: String = "security-lamp-region-fetch-failed"
        private const val FETCH_FAILED_TITLE: String = "보안등 지역 수집 실패"
        private const val NO_COORDINATES_KEY: String = "security-lamp-region-no-coordinates"
        private const val NO_COORDINATES_TITLE: String = "보안등 지역 좌표 없음"
    }
}
