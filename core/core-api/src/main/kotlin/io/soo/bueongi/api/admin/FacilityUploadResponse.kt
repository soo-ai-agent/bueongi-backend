package io.soo.bueongi.api.admin

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.domain.facility.FacilityCoordSystem
import io.soo.bueongi.domain.facility.FacilityUploadResult
import io.soo.bueongi.domain.facility.FacilityUploadService
import io.soo.bueongi.support.error.UnknownFacilityTypeException
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** 업로드 응답 — 반영된 종류·좌표계·수신/저장/스킵 집계. */
data class FacilityUploadResponse(
    /** 반영된 시설 종류. JSON 에는 cdnType(cctv|lamp|emergency_bell|safehouse|police)로 직렬화된다. */
    val type: FacilityType,
    /** 실제 사용된 좌표계. JSON 에는 wireName(wgs84|epsg5179|epsg5174)로 직렬화된다. 헤더에 위도/경도가 있으면 wgs84. */
    val coordSystem: FacilityCoordSystem,
    /** 파싱된 데이터 행 수(헤더 제외). */
    val received: Int,
    /** 저장·교체된 유효 시설 수. */
    val saved: Int,
    /** 좌표 없음·불량·비방범 CCTV 등으로 건너뛴 행 수(received - saved). */
    val skipped: Int,
) {
    companion object {
        fun from(result: FacilityUploadResult): FacilityUploadResponse = FacilityUploadResponse(
            type = result.type,
            coordSystem = result.coordSystem,
            received = result.received,
            saved = result.saved,
            skipped = result.skipped,
        )
    }
}
