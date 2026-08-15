package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.core.enums.SafetySpotType

interface SafetySpotLiveStore {
    /** 계약: 원자적 교체(조회는 전/후 한쪽의 일관 스냅샷만 본다), 반환 = 교체된 건수. */
    fun replace(type: SafetySpotType, points: List<SafetySpotPoint>): Int
}
