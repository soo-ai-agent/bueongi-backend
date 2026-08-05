package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType

/**
 * 안심시설 공간 질의 소스(점수/우회/마커가 의존하는 좁은 계약). [FacilityRepository] 가 유일 구현이며,
 * 인터페이스로 분리해 점수·우회 로직을 파일 적재 없이 가짜 소스로 단위 테스트할 수 있게 한다.
 */
interface FacilitySpatialSource {
    /** 시설 종류별 공간 인덱스. 미적재 종류는 빈 인덱스. */
    fun index(type: FacilityType): GridSpatialIndex

    /** 적재된 안심귀갓길 좌표열. */
    val safePaths: List<SafePath>

    /** 적재 데이터의 신선도(manifest 기반). 미상이면 null — 표기를 생략할 뿐 기능엔 영향 없다. */
    val freshness: FacilityFreshness?
        get() = null
}
