package io.soo.bueongi.core.common.route

import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint

interface RoutePlannedHandler {
    fun onRoutePlanned(points: List<GeoPoint>)
}
