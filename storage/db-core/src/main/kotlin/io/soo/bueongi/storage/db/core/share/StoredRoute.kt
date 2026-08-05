package io.soo.bueongi.storage.db.core.share

import java.time.Instant

/**
 * 저장된 길안내 경로 컨텍스트. 보호자 페이지가 앱과 동일한 지도를 재구성하는 데 필요한 최소 정보다 —
 * 목적지·경로선 좌표열·경로변 안심시설 마커·총 거리/시간. 공유 상한 만료(expiresAt)와 동일 TTL 로 함께 사라진다.
 * (웹 DTO 와 분리된 도메인 표현 — [StoredLocation] 과 동일 철학.)
 */
data class StoredRoute(
    val destination: StoredRouteDestination,
    val path: List<StoredCoord>,
    val markers: List<StoredMarker>,
    val totalDistanceM: Int?,
    val totalTimeS: Int?,
    /**
     * 경로 종류(safe|fast|main). 앱이 어떤 경로를 저장했는지 보호자 화면에 그대로 노출해 정보 패리티를 맞춘다.
     * 선택 — 미전송(구세션/미보고)이면 null(Redis JSON 은 필드 누락 시 Kotlin 기본값 null 로 역직렬화되어 하위호환).
     */
    val type: String? = null,
    /**
     * 시설 데이터 출처·기준일 문구(예: "서울시 안심이 CCTV 2026-03 기준"). 보호자에게 데이터 신뢰 맥락을 보여준다.
     * 선택 — 미전송이면 null([type] 과 동일한 하위호환 규약).
     */
    val provenance: String? = null,
)
