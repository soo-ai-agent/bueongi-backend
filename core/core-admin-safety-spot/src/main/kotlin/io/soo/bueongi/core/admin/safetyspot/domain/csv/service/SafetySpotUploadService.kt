package io.soo.bueongi.core.admin.safetyspot.domain.csv.service

import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotUploadResponse
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvContract
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvParseResult
import io.soo.bueongi.core.admin.safetyspot.domain.error.EmptySafetySpotUploadException
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.store.RegionLocator
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotUploadStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SafetySpotUploadService(
    private val parser: SafetySpotCsvParser,
    private val uploadStore: SafetySpotUploadStore,
    private val liveStore: SafetySpotLiveStore,
    private val refreshStatusStore: SafetySpotRefreshStatusStore,
    private val regionLocator: RegionLocator,
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotUploadService::class.java)

    fun upload(type: SafetySpotType, bytes: ByteArray): SafetySpotUploadResponse {
        val plain: ByteArray = gunzipIfNeeded(bytes)
        val contract: SafetySpotCsvContract = SafetySpotCsvContract.forType(type)
        val parsed: SafetySpotCsvParseResult = parser.parse(plain, contract) { point -> regionLocator.locate(point) }
        if (parsed.points.isEmpty()) {
            throw EmptySafetySpotUploadException(received = parsed.received)
        }
        uploadStore.save(type, parsed.points)
        uploadStore.saveOriginal(type, plain)
        liveStore.replaceFiles(type, parsed.points)
        val saved: Int = parsed.points.size
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
