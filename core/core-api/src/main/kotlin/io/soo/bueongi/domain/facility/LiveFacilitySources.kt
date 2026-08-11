package io.soo.bueongi.domain.facility

import io.soo.bueongi.core.enums.FacilityType

/** 라이브 갱신 대상 종류만 알려주는 좁은 창구 — 관리자 현황이 갱신 서비스 전체에 묶이지 않게 한다. */
interface LiveFacilitySources {
    fun liveRefreshableTypes(): Set<FacilityType>
}
