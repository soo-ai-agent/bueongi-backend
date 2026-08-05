package io.soo.bueongi.client.safemap

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 투영 평면 좌표(TM): [x]=동쪽(easting, m), [y]=북쪽(northing, m). [Epsg5179]·[Epsg5174] 정투영 결과. */
data class TmXy(val x: Double, val y: Double)
