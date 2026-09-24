package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

interface SafetySpotLiveStore {
    // 계약: 원자적 교체 — 조회는 전/후 한쪽의 일관 스냅샷만 본다.
    fun replace(type: SafetySpotType, points: List<SafetySpotPoint>): Int

    // 계약: 기존 인덱스에 원자적으로 더한다 — replace 는 다른 지역을 날린다.
    fun append(type: SafetySpotType, points: List<SafetySpotPoint>): Int
}
