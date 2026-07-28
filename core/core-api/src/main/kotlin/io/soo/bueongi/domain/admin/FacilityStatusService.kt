package io.soo.bueongi.domain.admin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshRecord
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import org.springframework.stereotype.Service

/**
 * 관리자 페이지용 "시설 데이터 현황" 집계 — 종류(5종) 각각의 현재 적재 건수 + 마지막 라이브 갱신 시각·결과 + CDN 신선도를 모은다.
 * 적재 건수는 저장소 공간 인덱스([FacilitySpatialSource.index])에서, 갱신 결과는 인메모리 홀더([FacilityRefreshStatusStore])에서,
 * CDN 신선도는 manifest 기반 [FacilitySpatialSource.freshness]에서 읽는다(집계만, 새 저장소·부수효과 없음).
 */
@Service
class FacilityStatusService(
    private val facilitySource: FacilitySpatialSource,
    private val refreshStatusStore: FacilityRefreshStatusStore,
) {

    fun summarize(): FacilityStatusSummary {
        val freshness: FacilityFreshness? = facilitySource.freshness
        val facilities: List<FacilityStatus> = FacilityType.entries.map { type ->
            val record: FacilityRefreshRecord? = refreshStatusStore.findByType(type)
            FacilityStatus(
                type = type,
                loadedCount = facilitySource.index(type).size,
                lastRefreshAtMs = record?.atEpochMs,
                lastRefreshCount = record?.replacedCount,
                lastRefreshStatus = statusOf(record?.replacedCount, attempted = record != null),
                cdnGeneratedAt = freshness?.generatedAt,
                cdnVersion = freshness?.version,
            )
        }
        return FacilityStatusSummary(facilities = facilities)
    }

    /** 갱신 기록 없음=NEVER, 있으면 교체 건수 유무로 REPLACED(성공)/KEPT(수집 실패·0건 유지) 판정. */
    private fun statusOf(replacedCount: Int?, attempted: Boolean): FacilityRefreshStatus {
        if (!attempted) {
            return FacilityRefreshStatus.NEVER
        }
        if (replacedCount != null) {
            return FacilityRefreshStatus.REPLACED
        }
        return FacilityRefreshStatus.KEPT
    }
}

/**
 * 시설 라이브 갱신 결과 3상태 — facility-status 응답 전용 판별자. 프론트 FacilityRefreshStatus('replaced'|'kept'|'never')와 대응한다.
 *
 * [wire] 가 와이어 문자열이다([JsonValue]) — lastRefreshStatus 필드가 이 값이라 직렬화가 정확히
 * replaced|kept|never 를 내야 한다. 역직렬화는 [from] 으로 고정한다([JsonCreator]).
 *
 * - NEVER: 아직 갱신 시도 없음(키 없는 원천 포함).
 * - REPLACED: 마지막 갱신에서 실제로 교체 성공(교체 건수 있음).
 * - KEPT: 갱신을 시도했으나 수집 실패·0건이라 기존 적재분을 유지.
 */
enum class FacilityRefreshStatus(@get:JsonValue val wire: String) {
    NEVER("never"),
    REPLACED("replaced"),
    KEPT("kept"),
    ;

    companion object {
        /** wire 문자열 → 상태. 알 수 없는 값은 예외(닫힌 집합) — 역직렬화 경계용. */
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityRefreshStatus = entries.first { it.wire == value }
    }
}

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

/** facility-status 응답 전체 — 종류별 현황 배열. */
data class FacilityStatusSummary(
    val facilities: List<FacilityStatus>,
)
