package io.soo.bueongi.core.api.controller.v1.request

import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.core.support.error.InvalidCoordinateException

fun toValidatedGeoPoint(lat: Double, lng: Double): GeoPoint =
    GeoPoint.orNull(lat, lng) ?: throw InvalidCoordinateException(lat, lng)
