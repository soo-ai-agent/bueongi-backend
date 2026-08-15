package io.soo.bueongi.core.facility.api.controller

import io.soo.bueongi.core.common.error.UnknownFacilityTypeException
import io.soo.bueongi.core.facility.api.response.FacilityUploadResponse

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.facility.domain.csv.model.FacilityUploadResult
import io.soo.bueongi.core.facility.domain.csv.service.FacilityUploadService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/admin/facilities")
class FacilityUploadController(
    private val uploadService: FacilityUploadService,
) {

    @PostMapping("/{type}/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable type: String,
        @RequestParam("file") file: MultipartFile,
    ): FacilityUploadResponse {
        val facilityType: FacilityType = findFacilityType(type)
        val result: FacilityUploadResult = uploadService.upload(facilityType, file.bytes)
        return FacilityUploadResponse.from(result)
    }

    /** 경로의 "cctv" 같은 문자열을 시설 종류로 바꾼다. 모르는 값이면 400 으로 돌린다. */
    private fun findFacilityType(cdnType: String): FacilityType {
        val found: FacilityType? = FacilityType.ofCdnTypeOrNull(cdnType)
        if (found == null) {
            throw UnknownFacilityTypeException(cdnType, FacilityType.CDN_TYPES)
        }
        return found
    }
}
