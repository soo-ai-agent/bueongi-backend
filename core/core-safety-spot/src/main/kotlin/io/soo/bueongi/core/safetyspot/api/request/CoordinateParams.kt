package io.soo.bueongi.core.safetyspot.api.request

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.core.safetyspot.domain.error.InvalidCoordinateException

fun toValidatedGeoPoint(lat: Double, lng: Double): GeoPoint =
    GeoPoint.orNull(lat, lng) ?: throw InvalidCoordinateException(lat, lng)
