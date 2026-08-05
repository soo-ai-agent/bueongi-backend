package io.soo.bueongi.domain.admin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshRecord
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import org.springframework.stereotype.Service

/** 시설 종류 1종의 데이터 현황. [type] 은 JSON 으로 cdnType(cctv/lamp/emergency_bell/safehouse/police)로 직렬화된다. */
data class FacilityStatus(
    val type: FacilityType,
    /** 현재 인메모리 인덱스에 적재된 건수(파일 적재분 + 라이브 교체분). */
    val loadedCount: Int,
    /** 마지막 라이브 갱신 시각(epoch ms). 갱신 시도 이력이 없으면 null. */
    val lastRefreshAtMs: Long?,
    /** 마지막 갱신에서 교체된 건수. 수집 실패·0건 유지(kept)거나 이력 없음(never)이면 null. */
    val lastRefreshCount: Int?,
    /** 마지막 갱신 결과(3상태). JSON 으로 [FacilityRefreshStatus.wire](replaced|kept|never)로 직렬화된다. */
    val lastRefreshStatus: FacilityRefreshStatus,
    /** CDN manifest 생성 시점(ISO-8601). 미상이면 null. */
    val cdnGeneratedAt: String?,
    /** CDN manifest 버전. 미상이면 null. */
    val cdnVersion: String?,
)
