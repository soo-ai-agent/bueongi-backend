package io.soo.bueongi.storage.db.safetyspot.model

// 한 점이 선분(시작~끝) 옆 어디에 있는지. progress 는 0 이 시작, 1 이 끝이고 0 보다 작으면 시작 앞, 1 보다 크면 끝 뒤다.
// distanceMeters 는 그 점에서 선분까지의 가장 짧은 거리다.
data class LinePosition(
    val progress: Double,
    val distanceMeters: Double,
)
