package io.soo.bueongi.share

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.Instant

/** 공유 만료 시간(시) 허용 범위 = 1~3시간. 무기한 추적 방지(안심앱 프라이버시) — 켜둔 채 잊어도 반드시 만료된다. */
const val MIN_EXPIRES_HOURS = 1

/** 공유 만료 시간 상한(시) = 3시간. MVP 계약 상한. */
const val MAX_EXPIRES_HOURS = 3

/** expires_in_hours 미지정 시 기본(시) = 1시간. 통상 귀가 1회를 덮되 가장 보수적인(짧은) 만료. */
const val DEFAULT_EXPIRES_HOURS = 1

/**
 * `POST /share/create` 요청. `expires_in_hours` 는 선택(없으면 [DEFAULT_EXPIRES_HOURS]).
 * 1~3 외의 값이면 400 VALIDATION_FAILED(기존 공통 에러 계약 재사용).
 */
data class CreateShareRequest(
    @field:Min(MIN_EXPIRES_HOURS.toLong())
    @field:Max(MAX_EXPIRES_HOURS.toLong())
    @JsonProperty("expires_in_hours")
    val expiresInHours: Int? = null,
)

/** `POST /share/create` 응답: 발급된 토큰 + 외부 메신저로 공유할 URL + 상한 만료 시각. */
data class CreateShareResponse(
    val token: String,
    @JsonProperty("share_url")
    val shareUrl: String,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
)

/**
 * `POST /share/{token}/location` 요청(사용자 단말, 5초마다). 좌표 규약은 routes 의 LatLng 와 동일 —
 * 범위(@DecimalMin/@DecimalMax) + 비유한(NaN/±Inf) 거부(Jackson 기본). MVP 계약은 `{lat, lng}` 만 받는다.
 */
data class UpdateLocationRequest(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
)

/** `POST /share/{token}/location` 응답: 갱신 시각 + 공유 상한 만료. */
data class UpdateLocationResponse(
    @JsonProperty("updated_at")
    val updatedAt: Instant,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
)

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
)
