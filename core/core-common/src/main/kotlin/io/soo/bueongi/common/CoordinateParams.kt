package io.soo.bueongi.common

import io.soo.bueongi.common.error.InvalidCoordinateException
import io.soo.bueongi.storage.db.core.facility.GeoPoint

fun toValidatedGeoPoint(lat: Double, lng: Double): GeoPoint =
    GeoPoint.orNull(lat, lng) ?: throw InvalidCoordinateException(lat, lng)
