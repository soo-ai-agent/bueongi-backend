package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/** 경로 목적지(좌표 + 표시명) — 보호자 응답 전용. name 은 선택(집·회사 등 라벨). */
data class RouteDestination(
    val name: String? = null,
    val lat: Double,
    val lng: Double,
)
