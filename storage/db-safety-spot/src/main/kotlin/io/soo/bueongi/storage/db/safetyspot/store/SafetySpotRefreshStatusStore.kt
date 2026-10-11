package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotRefreshRecord
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

// 업로드가 동시에 와도 다른 종류의 기록을 덮지 않게 종류별로 병합한다.
@Component
class SafetySpotRefreshStatusStore {
    private val records: AtomicReference<Map<SafetySpotType, SafetySpotRefreshRecord>> =
        AtomicReference<Map<SafetySpotType, SafetySpotRefreshRecord>>(emptyMap())

    fun recordUpload(type: SafetySpotType, replacedCount: Int, atEpochMs: Long) {
        val recorded: SafetySpotRefreshRecord = SafetySpotRefreshRecord(atEpochMs = atEpochMs, replacedCount = replacedCount)
        records.updateAndGet { current -> current + (type to recorded) }
    }

    fun findByType(type: SafetySpotType): SafetySpotRefreshRecord? {
        return records.get()[type]
    }
}
