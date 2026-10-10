package io.soo.bueongi.core.safetyspot.domain.fill.service

import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.core.common.event.SafetySpotFetchPerformed
import io.soo.bueongi.core.enums.AlertCategory
import io.soo.bueongi.core.enums.SafetySpotType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

// 수집 범위 — 전국 한 덩이로 받는 것은 NATIONWIDE, 지역·격자로 나눠 받는 것은 그 코드.
const val NATIONWIDE: String = "nationwide"

@Component
class SafetySpotFillEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun fetchPerformed(type: SafetySpotType, scope: String, receivedCount: Int?) {
        events.publishEvent(SafetySpotFetchPerformed(type = type, scope = scope, receivedCount = receivedCount))
    }

    fun fetchFailed(type: SafetySpotType, scope: String) {
        events.publishEvent(
            OperationEvent(
                key = AlertCategory.SAFETY_SPOT_FETCH_FAILED.keyOf(type.cdnType),
                title = "${type.label} 수집 실패",
                detail = "${scopeText(scope)}${type.label} 수집 실패 — 기존 데이터 유지, 다음 요청에서 재시도",
            ),
        )
    }

    fun hadNoCoordinates(type: SafetySpotType, scope: String, fetched: Int) {
        events.publishEvent(
            OperationEvent(
                key = AlertCategory.SAFETY_SPOT_NO_COORDINATES.keyOf(type.cdnType),
                title = "${type.label} 좌표 없음",
                detail = "${scopeText(scope)}${type.label} ${fetched}건을 받았으나 좌표가 없어 적재 0건",
            ),
        )
    }

    private fun scopeText(scope: String): String {
        if (scope == NATIONWIDE) {
            return ""
        }
        return "$scope "
    }
}
