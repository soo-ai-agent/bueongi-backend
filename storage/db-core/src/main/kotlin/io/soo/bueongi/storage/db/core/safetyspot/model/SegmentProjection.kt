package io.soo.bueongi.storage.db.core.safetyspot.model

// t 는 클램프 전 진행 매개변수 — 0 미만=선분 시작 이전, 1 초과=끝 이후.
data class SegmentProjection(
    val t: Double,
    val distanceMeters: Double,
)
