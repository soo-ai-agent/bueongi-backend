package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/**
 * 경로 좌표 1점(경로선) — 보호자 응답 전용 값 타입. 저장 모델([StoredRoute])의 비null 좌표를 그대로 실으므로
 * 검증 어노테이션이 없다(요청 검증은 api/v1/request 의 LatLng 이 담당 — 웹 검증 관심사를 도메인에 두지 않는다).
 */
data class RoutePoint(
    val lat: Double,
    val lng: Double,
)
