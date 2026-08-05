package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType
import org.springframework.stereotype.Component

/**
 * 마지막 라이브 갱신 결과(종류별)를 담는 인메모리 홀더. 갱신은 기동 시 1회 + 월 1회라 최근 1회분만 보관하고
 * 재시작 시 휘발해도 무방하다(Redis 불필요) — 공유 저장소의 인메모리 폴백과 같은 철학(단일 인스턴스 가정).
 *
 * 갱신 서비스([io.soo.bueongi.domain.facility.FacilityRefreshService])가 refreshAll 종료 시 반환 Map 을 그대로 기록하고,
 * 관리자 상태 집계가 종류별로 조회한다. `@Volatile` 재대입이라 조회 스레드는 기록 전/후 어느 한쪽의 일관된 스냅샷을 본다.
 */
@Component
class FacilityRefreshStatusStore {

    @Volatile
    private var records: Map<FacilityType, FacilityRefreshRecord> = emptyMap()

    /**
     * [results] = refreshAll 반환(종류별 교체 건수, 실패/0건 유지는 null, 키 없는 원천의 종류는 미포함).
     * 미포함 종류는 기록되지 않으므로 조회 시 null → 호출측이 "never(갱신 시도 없음)"로 해석한다.
     */
    fun record(results: Map<FacilityType, Int?>, atEpochMs: Long) {
        records = results.mapValues { (_, count) -> FacilityRefreshRecord(atEpochMs = atEpochMs, replacedCount = count) }
    }

    /**
     * 관리자 업로드에 의한 [type] 단일 종류 교체를 기록한다(다른 종류 기록은 보존 — 전체 교체인 [record] 와 다르다).
     * 업로드는 항상 교체 성공이므로 [replacedCount] 는 저장 건수(비null) → 조회 시 replaced 로 판정된다.
     */
    fun recordUpload(type: FacilityType, replacedCount: Int, atEpochMs: Long) {
        records = records + (type to FacilityRefreshRecord(atEpochMs = atEpochMs, replacedCount = replacedCount))
    }

    /** [type] 의 마지막 갱신 기록. 아직 갱신을 시도한 적 없으면 null. */
    fun findByType(type: FacilityType): FacilityRefreshRecord? = records[type]
}
