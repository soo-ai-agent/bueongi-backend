package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotRefreshRecord
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

// 기록자가 둘이라(라이브 갱신 스케줄러 · 관리자 업로드) 통째로 덮지 않고 종류별로 병합한다.
@Component
class SafetySpotRefreshStatusStore {
    private val records: AtomicReference<Map<SafetySpotType, SafetySpotRefreshRecord>> =
        AtomicReference<Map<SafetySpotType, SafetySpotRefreshRecord>>(emptyMap())

    fun record(
        results: Map<SafetySpotType, Int?>,
        atEpochMs: Long,
    ) {
        val recorded: Map<SafetySpotType, SafetySpotRefreshRecord> =
            results.mapValues { (_, count) ->
                SafetySpotRefreshRecord(atEpochMs = atEpochMs, replacedCount = count)
            }
        records.updateAndGet { current -> current + recorded }
    }

    fun recordUpload(
        type: SafetySpotType,
        replacedCount: Int,
        atEpochMs: Long,
    ) {
        val recorded = SafetySpotRefreshRecord(atEpochMs = atEpochMs, replacedCount = replacedCount)
        records.updateAndGet { current -> current + (type to recorded) }
    }

    fun findByType(type: SafetySpotType): SafetySpotRefreshRecord? = records.get()[type]
}
