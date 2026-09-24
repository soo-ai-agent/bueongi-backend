package io.soo.bueongi.core.common.route

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

// 경로가 정해지면 그 경로의 좌표를 받아 할 일을 한다. 경로 도메인은 구현이 누구인지 모른다.
interface RoutePlannedHandler {
    fun onRoutePlanned(points: List<GeoPoint>)
}
