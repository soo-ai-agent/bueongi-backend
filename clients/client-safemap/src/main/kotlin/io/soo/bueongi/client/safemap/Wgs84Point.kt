package io.soo.bueongi.client.safemap

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** WGS84 한 점(치안시설 좌표 변환 결과). */
data class Wgs84Point(val lat: Double, val lng: Double)
