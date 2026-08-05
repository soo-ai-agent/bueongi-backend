package io.soo.bueongi.storage.db.core.facility

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 점을 선분에 투영한 결과. [t] 는 클램프 전 진행 매개변수(0 미만=선분 시작 이전, 1 초과=끝 이후),
 * [distanceMeters] 는 그 점에서 선분까지의 최단거리(m).
 */
data class SegmentProjection(val t: Double, val distanceMeters: Double)
