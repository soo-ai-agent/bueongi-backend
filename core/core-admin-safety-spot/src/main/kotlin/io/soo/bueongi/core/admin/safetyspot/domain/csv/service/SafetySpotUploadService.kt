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
        // ponytail: 약 1km 칸(소수 2자리)마다 한 번만 찾는다 — 경계 1km 안의 행은 이웃 지역으로 붙을 수 있다. 정확도가 문제되면 행정경계 폴리곤으로.
        val regionByCell: HashMap<String, String?> = HashMap()
        val parsed: SafetySpotCsvParseResult =
            parser.parse(plain, contract) { point ->
                val cell: String = "%.2f,%.2f".format(point.lat, point.lng)
                if (!regionByCell.containsKey(cell)) {
                    regionByCell[cell] = regionLocator.locate(point)
                }
                regionByCell[cell]
            }
        if (parsed.points.isEmpty()) {
            throw EmptySafetySpotUploadException(received = parsed.received)
        }
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
