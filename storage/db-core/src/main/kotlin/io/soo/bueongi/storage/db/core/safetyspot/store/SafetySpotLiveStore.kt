package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.core.enums.SafetySpotType

interface SafetySpotLiveStore {
    /** 계약: 원자적 교체(조회는 전/후 한쪽의 일관 스냅샷만 본다), 반환 = 교체된 건수. */
    fun replace(type: SafetySpotType, points: List<SafetySpotPoint>): Int

    /**
     * 계약: 기존 인덱스에 원자적으로 **더한다**. 반환 = 더한 뒤 총 건수.
     * 지역 단위로 받아 온 데이터를 이미 있는 것과 합칠 때 쓴다 — [replace] 는 다른 지역을 날린다.
     */
    fun append(type: SafetySpotType, points: List<SafetySpotPoint>): Int
}
