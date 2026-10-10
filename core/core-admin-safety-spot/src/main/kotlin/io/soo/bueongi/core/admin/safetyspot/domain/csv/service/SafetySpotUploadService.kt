package io.soo.bueongi.core.admin.safetyspot.domain.csv.service

import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotUploadResponse
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvParseResult
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvSpec
import io.soo.bueongi.core.admin.safetyspot.domain.error.EmptySafetySpotUploadException
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotUploadStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SafetySpotUploadService(
    private val parser: SafetySpotCsvParser,
    private val uploadStore: SafetySpotUploadStore,
    private val liveStore: SafetySpotLiveStore,
    private val refreshStatusStore: SafetySpotRefreshStatusStore,
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotUploadService::class.java)

    fun upload(type: SafetySpotType, bytes: ByteArray): SafetySpotUploadResponse {
        val spec: SafetySpotCsvSpec = SafetySpotCsvSpec.forType(type)
        val parsed: SafetySpotCsvParseResult = parser.parse(bytes, spec)
        if (parsed.points.isEmpty()) {
            throw EmptySafetySpotUploadException(received = parsed.received)
        }
        uploadStore.save(type, parsed.points)
        uploadStore.saveOriginal(type, bytes)
        val saved: Int = liveStore.replace(type, parsed.points)
        refreshStatusStore.recordUpload(type, saved, System.currentTimeMillis())
        log.info(
            "시설 업로드 반영: type={}, coordSystem={}, received={}, saved={}, skipped={}",
            type.cdnType,
            parsed.coordSystem.wireName,
            parsed.received,
            saved,
            parsed.skipped,
        )
        return SafetySpotUploadResponse.from(type, parsed, saved)
    }
}
