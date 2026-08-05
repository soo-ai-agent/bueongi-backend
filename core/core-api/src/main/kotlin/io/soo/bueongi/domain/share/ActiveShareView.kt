package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/**
 * `GET /api/admin/share-sessions` 목록 1건(관리자 전용) — 운영자가 활성 공유를 감시하는 뷰. 도메인 결과이자
 * 어드민 응답 계약이다. 어드민 관례(camelCase)를 따르므로 보호자용 스네이크 케이스 결과들과 달리 @JsonProperty 가 없다.
 * 프라이버시: 읽기 토큰·공유 URL·현재 위치·TTL·시청 여부만 싣고, owner_secret·경로 상세는 절대 포함하지 않는다.
 */
data class ActiveShareView(
    /** 공유 URL에 담기는 읽기 전용 토큰(보호자가 위치를 조회). 쓰기 비밀(owner_secret)은 절대 싣지 않는다. */
    val token: String,
    /** 외부 공유용 URL(`{base}/share/{token}`). 도메인에서 조립한다(컨트롤러는 조립하지 않는다). */
    val shareUrl: String,
    /** 마지막 보고 위치 위도. 첫 위치 보고 전이면 null. */
    val lat: Double?,
    /** 마지막 보고 위치 경도. 첫 위치 보고 전이면 null. */
    val lng: Double?,
    /** 마지막 위치 갱신 시각(epoch ms). 첫 위치 보고 전이면 null. */
    val updatedAtMs: Long?,
    /** 공유 상한 만료까지 남은 시간(초). 세션 expiresAt 에서 파생(음수는 0으로 절삭). */
    val ttlSeconds: Long,
    /** 최근 유효시간 내 보호자(뷰어) 조회가 있었는지(=현재 누군가 보는 중). */
    val viewerActive: Boolean,
)
