package io.soo.bueongi.core.share.domain.model

import io.soo.bueongi.core.share.domain.route.model.RouteContext
import io.soo.bueongi.core.share.domain.route.model.RouteDestination
import io.soo.bueongi.core.share.domain.route.model.RouteMarker
import io.soo.bueongi.core.share.domain.route.model.RoutePoint
import io.soo.bueongi.storage.db.core.share.model.StoredRoute

const val MIN_EXPIRES_HOURS: Int = 1

const val MAX_EXPIRES_HOURS: Int = 3

const val DEFAULT_EXPIRES_HOURS: Int = 1

const val MAX_ROUTE_PATH_POINTS: Int = 4000

const val MAX_ROUTE_MARKERS: Int = 300

const val ROUTE_MARKER_TYPE_PATTERN: String = "cctv|bell|store|police|safehouse|lamp|pedestrian_signal"

// RouteType.from 은 미상을 SAFE 로 관용 처리해 재사용 불가 — 여기는 엄격 거부(위반 시 422)용 명시 집합.
val ROUTE_TYPE_VALUES: Set<String> = setOf("safe", "fast", "main")

const val MAX_ROUTE_PROVENANCE_LENGTH: Int = 200

const val MISSED_ARRIVAL_GRACE_SECONDS: Long = 60L

fun StoredRoute.toRouteContext(): RouteContext =
    RouteContext(
        destination = RouteDestination(name = destination.name, lat = destination.lat, lng = destination.lng),
        path = path.map { RoutePoint(lat = it.lat, lng = it.lng) },
        markers =
            markers.map { marker ->
                RouteMarker(
                    type = marker.type,
                    lat = marker.lat,
                    lng = marker.lng,
                    x = marker.x,
                    y = marker.y,
                    name = marker.name,
                    purpose = marker.purpose,
                    cameraCount = marker.cameraCount,
                    address = marker.address,
                    phone = marker.phone,
                )
            },
        totalDistanceM = totalDistanceM,
        totalTimeS = totalTimeS,
        type = type,
        provenance = provenance,
    )
