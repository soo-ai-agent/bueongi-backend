package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType

/**
 * 시설 라이브 갱신 싱크 — 도메인 갱신 서비스(core-api FacilityRefreshService)가 공공데이터 API 수집분으로
 * 종류별 인덱스를 교체할 때 쓰는 좁은 계약. [FacilityRepository] 가 유일 구현이며, 인터페이스로 분리해
 * 갱신 오케스트레이션을 파일 적재 없이 가짜 싱크로 단위 테스트할 수 있게 한다.
 */
interface FacilityLiveStore {
    /**
     * [type] 의 공간 인덱스를 [points] 로 **원자적으로 교체**한다(다른 종류·안심귀갓길은 불변).
     * 조회 중인 스레드는 교체 전/후 어느 한쪽의 일관된 스냅샷을 본다. 반환 = 교체된 건수.
     */
    fun replace(type: FacilityType, points: List<FacilityPoint>): Int
}
