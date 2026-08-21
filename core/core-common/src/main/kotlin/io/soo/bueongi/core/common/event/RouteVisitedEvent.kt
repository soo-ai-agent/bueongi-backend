package io.soo.bueongi.core.common.event

/**
 * 사용자가 이 좌표들이 있는 곳의 경로를 조회했다.
 *
 * 경로 도메인은 이 사건만 낸다 — 누가 받아 무엇을 채우는지(안심시설 지연 적재 등)는 모른다.
 * 좌표는 위경도 쌍이며, 도메인 타입 대신 원시 값으로 실어 모듈 결합을 만들지 않는다.
 */
data class RouteVisitedEvent(val points: List<VisitedPoint>)

data class VisitedPoint(val lat: Double, val lng: Double)
