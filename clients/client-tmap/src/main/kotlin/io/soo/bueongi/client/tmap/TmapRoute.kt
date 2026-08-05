package io.soo.bueongi.client.tmap

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tmap 보행자 경로 1건. path 는 경로 라인 좌표열(안전 점수/마커의 원천), steps 는 단계별 안내(프론트 NavStep 의미).
 */
data class TmapRoute(
    val searchOption: String,
    val path: List<TmapCoord>,
    val distanceM: Double,
    val timeS: Int,
    val steps: List<NavStep>,
) {
    /**
     * 같은 경로 판정 키(거리 + 좌표수). 옵션만 다르고 실제로 같은 길인 후보의 중복 제거에 쓴다 —
     * 클라이언트 수집([TmapRouteClient.fetchRoutes])과 도메인 후보 합류(SafeRouteService)가
     * 동일 판정을 공유하는 단일 출처.
     *
     * 거리(m)를 [PATH_SIZE_SLOT] 배로 밀어 하위 자리에 좌표수를 담는다 — 좌표수가 슬롯(1000) 미만이라는
     * 전제이며, 초과 시 거리 슬롯과 겹쳐 다른 경로가 같은 키를 가질 수 있다(도보 경로에선 충분한 여유).
     */
    val identityKey: Long
        get() = (distanceM.roundToLong() * PATH_SIZE_SLOT) + path.size

    companion object {
        /** identityKey 의 좌표수 슬롯 크기 — 거리와 좌표수를 한 Long 에 패킹하는 자릿수 기준. */
        const val PATH_SIZE_SLOT: Long = 1_000L
    }
}
