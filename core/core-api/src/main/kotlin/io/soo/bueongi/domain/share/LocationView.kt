package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/**
 * `GET /share/{token}/location` 응답(보호자 웹 5초 폴링). 200 으로만 응답하고 상태는 [expired]/위치 null 로 표현한다:
 * - 토큰 없음/만료: `{ expired: true }`(좌표 null) — 보호자 UI 가 "공유 종료/만료" 화면.
 * - 살아있으나 첫 위치 전: `{ expired: false, lat: null, lng: null }` — "위치 받는 중".
 * - 위치 있음: `{ lat, lng, updated_at, expired: false }`.
 */
data class LocationView(
    val lat: Double?,
    val lng: Double?,
    @JsonProperty("updated_at")
    val updatedAt: Instant?,
    val expired: Boolean,
    /** 목적지까지 남은 거리(미터). 앱이 보고했을 때만 채워지고, 아니면 null(보호자 UI 는 표시 생략). */
    @JsonProperty("remaining_m")
    val remainingM: Double? = null,
    /** 목적지까지 남은 예상 시간(초). [remainingM] 과 동일 규약(보고 시에만 채움). */
    @JsonProperty("remaining_s")
    val remainingS: Double? = null,
    /**
     * 미도착 의심 여부 — 예정 도착 시각을 여유([MISSED_ARRIVAL_OVERDUE_SECONDS]) 넘겨 초과했는지. ETA 미제공 세션은 false.
     * 보호자 페이지가 B(staleness) 경고 위에 "예정 시간 초과 — 연락해 보세요" 격상을 그릴 근거로 쓴다(FE 후속).
     */
    val overdue: Boolean = false,
)
