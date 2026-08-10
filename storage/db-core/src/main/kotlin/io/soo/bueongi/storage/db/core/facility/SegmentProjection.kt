package io.soo.bueongi.storage.db.core.facility

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** [t] 는 클램프 전 진행 매개변수 — 0 미만=선분 시작 이전, 1 초과=끝 이후. */
data class SegmentProjection(val t: Double, val distanceMeters: Double)
