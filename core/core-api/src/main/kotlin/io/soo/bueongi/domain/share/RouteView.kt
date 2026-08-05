package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/**
 * `GET /share/{token}/route` 응답(보호자, 인증 없음·1회 fetch). 위치 조회([LocationView])와 같은 결로
 * 항상 200 이고 상태는 [expired]/[route] null 로 표현한다 — 토큰 없음/만료 → `{expired:true, route:null}`,
 * 살아있으나 아직 경로 미저장 → `{expired:false, route:null}`, 저장됨 → `{expired:false, route:{...}}`.
 * 이 조회는 위치 폴링(GET /location)과 달리 시청자로 집계하지 않는다.
 */
data class RouteView(
    val expired: Boolean,
    val route: RouteContext?,
)
