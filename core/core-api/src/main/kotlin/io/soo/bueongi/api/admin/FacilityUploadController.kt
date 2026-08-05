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

/**
 * 관리자 시설 CSV 업로드 API. `/api/admin` 하위 경로는 SecurityConfig 가 ROLE_ADMIN 으로 보호한다(엔드포인트별 게이트 불필요).
 * 컨트롤러는 변환만 한다 — path 종류 해석·파일 바이트 추출·도메인 위임·결과 DTO 매핑. 파싱·좌표변환·저장·교체는 [FacilityUploadService] 가 지휘한다.
 */
@RestController
@RequestMapping("/api/admin/facilities")
class FacilityUploadController(
    private val uploadService: FacilityUploadService,
) {

    /**
     * POST /api/admin/facilities/{type}/upload (multipart/form-data, 파일 파트 `file`) → 표준 CSV 를 파싱·교체하고 집계를 반환.
     * {type} 은 cdnType(cctv|lamp|emergency_bell|safehouse|police). 알 수 없는 종류는 400(UNKNOWN_FACILITY_TYPE),
     * 유효 0건은 400(VALIDATION_FAILED), 필수 파트 누락은 400(VALIDATION_FAILED) — 변환은 ApiControllerAdvice 가 한다.
     */
    @PostMapping("/{type}/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable type: String,
        @RequestParam("file") file: MultipartFile,
    ): FacilityUploadResponse {
        val facilityType: FacilityType = FacilityType.entries.find { it.cdnType == type }
            ?: throw UnknownFacilityTypeException(type, FacilityType.entries.map { it.cdnType })
        val result: FacilityUploadResult = uploadService.upload(facilityType, file.bytes)
        return FacilityUploadResponse.from(result)
    }
}
