package io.soo.bueongi.facility

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilityUploadStore
import io.soo.bueongi.common.error.EmptyFacilityUploadException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FacilityUploadService(
    private val parser: FacilityCsvParser,
    private val uploadStore: FacilityUploadStore,
    private val liveStore: FacilityLiveStore,
    private val refreshStatusStore: FacilityRefreshStatusStore? = null,
) {
    private val log: Logger = LoggerFactory.getLogger(FacilityUploadService::class.java)

    fun upload(type: FacilityType, bytes: ByteArray): FacilityUploadResult {
        val spec: FacilityCsvSpec = FacilityCsvSpec.forType(type)
        val parsed: FacilityCsvParseResult = parser.parse(bytes, spec)
        if (parsed.points.isEmpty()) {
            throw EmptyFacilityUploadException(received = parsed.received)
        }
        uploadStore.save(type, parsed.points)
        val saved: Int = liveStore.replace(type, parsed.points)
        refreshStatusStore?.recordUpload(type, saved, System.currentTimeMillis())
        log.info(
            "시설 업로드 반영: type={}, coordSystem={}, received={}, saved={}, skipped={}",
            type.cdnType,
            parsed.coordSystem.wireName,
            parsed.received,
            saved,
            parsed.skipped,
        )
        return FacilityUploadResult(
            type = type,
            coordSystem = parsed.coordSystem,
            received = parsed.received,
            saved = saved,
            skipped = parsed.skipped,
        )
    }
}
