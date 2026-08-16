package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotRefreshRecord
import io.soo.bueongi.core.enums.SafetySpotType
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/**
 * 종류별 마지막 갱신 기록. 기록자가 둘이라(라이브 갱신 스케줄러 · 관리자 업로드 요청) 원자적으로 병합한다.
 * 이번 갱신에 없던 종류의 기록은 남긴다 — 통째로 덮으면 업로드 이력이 다음 정기 갱신 때 사라진다.
 */
@Component
class SafetySpotRefreshStatusStore {

    private val records: AtomicReference<Map<SafetySpotType, SafetySpotRefreshRecord>> =
        AtomicReference<Map<SafetySpotType, SafetySpotRefreshRecord>>(emptyMap())

    fun record(results: Map<SafetySpotType, Int?>, atEpochMs: Long) {
        val recorded: Map<SafetySpotType, SafetySpotRefreshRecord> = results.mapValues { (_, count) ->
            SafetySpotRefreshRecord(atEpochMs = atEpochMs, replacedCount = count)
        }
        records.updateAndGet { current -> current + recorded }
    }

    fun recordUpload(type: SafetySpotType, replacedCount: Int, atEpochMs: Long) {
        val recorded = SafetySpotRefreshRecord(atEpochMs = atEpochMs, replacedCount = replacedCount)
        records.updateAndGet { current -> current + (type to recorded) }
    }

    fun findByType(type: SafetySpotType): SafetySpotRefreshRecord? =
        records.get()[type]
}
