package io.soo.bueongi.domain.facility

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilityUploadStore
import io.soo.bueongi.support.error.EmptyFacilityUploadException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 관리자 시설 CSV 업로드 오케스트레이션 — 파싱 → 디스크 영속 → 인메모리 원자 교체 순으로 지휘한다.
 *
 * 디스크 저장([FacilityUploadStore])을 먼저 해서, 교체 후 재기동해도 마지막 업로드가 살아있게 한다(업로드 우선 적재).
 * 유효 시설 0건(빈 파일·좌표 없는 행뿐)이면 교체하지 않고 [EmptyFacilityUploadException] 을 던진다(기존 데이터 보존).
 */
@Service
class FacilityUploadService(
    private val parser: FacilityCsvParser,
    private val uploadStore: FacilityUploadStore,
    private val liveStore: FacilityLiveStore,
    /** 관리자 상태 조회용 갱신 기록(없으면 null 무해) — 업로드를 replaced 이벤트로 남긴다. 순수 부수효과. */
    private val refreshStatusStore: FacilityRefreshStatusStore? = null,
) {
    private val log = LoggerFactory.getLogger(FacilityUploadService::class.java)

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

/** 업로드 반영 결과 — 컨트롤러가 응답 DTO 로 변환한다. */
data class FacilityUploadResult(
    val type: FacilityType,
    val coordSystem: FacilityCoordSystem,
    val received: Int,
    val saved: Int,
    val skipped: Int,
)
