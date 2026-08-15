package io.soo.bueongi.core.facility.api.request

import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.core.common.error.InvalidCoordinateException

fun toValidatedGeoPoint(lat: Double, lng: Double): GeoPoint =
    GeoPoint.orNull(lat, lng) ?: throw InvalidCoordinateException(lat, lng)
