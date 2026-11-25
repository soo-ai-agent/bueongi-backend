package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

// 서버가 켜질 때 배포 CDN 파일의 시설을 DB 의 'cdn' 조각으로 맞춘다. 업로드가 있는 종류는 건드리지 않는다.
@Component
class SafetySpotCdnSync(
    private val cdnFiles: SafetySpotCdnFiles,
    private val store: PostgisSafetySpotStore,
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotCdnSync::class.java)

    @PostConstruct
    fun sync() {
        for ((type, points) in cdnFiles.pointsByType) {
            syncType(type, points)
        }
    }

    private fun syncType(type: SafetySpotType, points: List<SafetySpotPoint>) {
        val replaced: Boolean = store.replaceCdnUnlessUploaded(type, points)
        if (replaced) {
            log.info("안심시설 CDN 반영: type={}, count={}", type.cdnType, points.size)
            return
        }
        log.info("안심시설 CDN 건너뜀(업로드가 대신함): type={}", type.cdnType)
    }
}
