package io.soo.bueongi.storage.db.share.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 칸을 지우고 배포해도, 그 전에 저장된 경로(공유 수명 최대 3시간)가 지운 칸 때문에 못 읽히지 않게 한다.
@JsonIgnoreProperties(ignoreUnknown = true)
data class StoredRoute(
    val destination: StoredRouteDestination,
    val path: List<StoredCoord>,
    val markers: List<StoredMarker>,
    // 이름을 바꾸기 전에 저장된 경로(공유 수명 최대 3시간)도 읽게 옛 이름도 받는다.
    @param:JsonAlias("totalDistanceM")
    val totalDistanceMeters: Int,
    @param:JsonAlias("totalTimeS")
    val totalTimeSeconds: Int,
    // 허용값은 RouteType 의 apiType 이다.
    val type: String,
)
