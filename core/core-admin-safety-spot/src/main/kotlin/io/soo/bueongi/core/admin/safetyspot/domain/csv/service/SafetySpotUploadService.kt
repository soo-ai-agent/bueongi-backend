package io.soo.bueongi.core.admin.safetyspot.domain.csv.service

import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotUploadResponse
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.CellRegionCodeFinder
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvFormat
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvReadResult
import io.soo.bueongi.core.admin.safetyspot.domain.error.EmptySafetySpotUploadException
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.store.RegionCodeFinder
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotUploadStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotWriter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SafetySpotUploadService(
    private val reader: SafetySpotCsvReader,
    private val uploadStore: SafetySpotUploadStore,
    private val safetySpotWriter: SafetySpotWriter,
    private val refreshStatusStore: SafetySpotRefreshStatusStore,
    private val regionCodeFinder: RegionCodeFinder,
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotUploadService::class.java)

    fun upload(type: SafetySpotType, bytes: ByteArray): SafetySpotUploadResponse {
        val csvBytes: ByteArray = unzipIfNeeded(bytes)
        val format: SafetySpotCsvFormat = SafetySpotCsvFormat.forType(type)
        val cellRegionCodeFinder = CellRegionCodeFinder(regionCodeFinder)

        val result: SafetySpotCsvReadResult = reader.readPoints(csvBytes, format, cellRegionCodeFinder)
        if (result.points.isEmpty()) {
            throw EmptySafetySpotUploadException(received = result.received)
        }

        uploadStore.saveOriginal(type, csvBytes)
        safetySpotWriter.replaceUpload(type, result.points)
        val saved: Int = result.points.size
        refreshStatusStore.recordUpload(type, saved, System.currentTimeMillis())
        log.info(
            "시설 업로드 반영: type={}, coordSystem={}, received={}, saved={}, skipped={}",
            type.cdnType,
            result.coordSystem.wireName,
            result.received,
            saved,
            result.skipped,
        )
        return SafetySpotUploadResponse.from(type, result, saved)
    }
}
