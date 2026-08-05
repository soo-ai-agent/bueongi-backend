package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/** 저장된 경로 컨텍스트의 보호자 응답 형태. 저장 요청과 동일 필드명(snake_case)으로 왕복한다. */
data class RouteContext(
    val destination: RouteDestination,
    val path: List<RoutePoint>,
    val markers: List<RouteMarker>,
    @JsonProperty("total_distance_m")
    val totalDistanceM: Int?,
    @JsonProperty("total_time_s")
    val totalTimeS: Int?,
    /** 경로 종류(safe|fast|main). 저장 시 함께 온 값을 그대로 에코 — 미저장이면 null(보호자 UI 표시 생략). */
    val type: String? = null,
    /** 시설 데이터 출처·기준일 문구. 저장된 값을 그대로 에코 — 미저장이면 null. */
    val provenance: String? = null,
)
