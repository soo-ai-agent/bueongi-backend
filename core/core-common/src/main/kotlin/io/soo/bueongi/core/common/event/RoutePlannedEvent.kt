package io.soo.bueongi.core.common.event

data class RoutePlannedEvent(val points: List<RoutePoint>)

data class RoutePoint(val lat: Double, val lng: Double)
