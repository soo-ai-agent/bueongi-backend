package io.soo.bueongi.storage.db.core.facility.store

import io.soo.bueongi.storage.db.core.facility.model.FacilityPoint
import io.soo.bueongi.core.enums.FacilityType

interface FacilityLiveStore {
    /** 계약: 원자적 교체(조회는 전/후 한쪽의 일관 스냅샷만 본다), 반환 = 교체된 건수. */
    fun replace(type: FacilityType, points: List<FacilityPoint>): Int
}
