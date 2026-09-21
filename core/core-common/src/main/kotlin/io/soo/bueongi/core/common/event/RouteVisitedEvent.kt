package io.soo.bueongi.core.common.event

data class RouteVisitedEvent(val points: List<VisitedPoint>)

data class VisitedPoint(val lat: Double, val lng: Double)
