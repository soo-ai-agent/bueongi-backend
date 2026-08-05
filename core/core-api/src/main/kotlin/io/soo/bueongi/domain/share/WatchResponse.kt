package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/**
 * `POST /share/{token}/watching` 응답(사용자 본인 = 관리자 전용). 두 가지를 함께 싣는다:
 * - [watching]: 최근 유효시간 내 보호자(뷰어) 조회가 있었는지(누군가 보는 중).
 * - 현재 공유 위치([lat]/[lng]/[updatedAt]/[expired]): 관리자가 보호자에게 보이는 화면을 '따로' 미리볼 수 있게 한다.
 *
 * 이 조회는 owner_secret 인증을 거치고 시청자로 집계되지 않으므로([ShareService.getWatchingStatus]),
 * 관리자 본인의 미리보기가 '보호자 시청 중' 알림을 만들지 않는다(읽기 토큰 GET /location 과 분리).
 * 위치 표현은 [LocationView] 와 동일 계약: 토큰 없음/만료 → expired:true(좌표 null), 첫 위치 전 → 좌표 null.
 */
data class WatchResponse(
    val watching: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
    @JsonProperty("updated_at")
    val updatedAt: Instant? = null,
    val expired: Boolean = false,
    /** 목적지까지 남은 거리(미터) — 관리자 미리보기를 보호자 화면과 일치시키려 [LocationView] 와 동일하게 싣는다. */
    @JsonProperty("remaining_m")
    val remainingM: Double? = null,
    /** 목적지까지 남은 예상 시간(초). [remainingM] 과 동일 규약. */
    @JsonProperty("remaining_s")
    val remainingS: Double? = null,
)
