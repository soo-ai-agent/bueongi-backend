package io.soo.bueongi.core.api.controller.v1

import io.soo.bueongi.core.api.controller.v1.response.FacilityUploadResponse

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.domain.facility.csv.FacilityUploadResult
import io.soo.bueongi.core.domain.facility.csv.FacilityUploadService
import io.soo.bueongi.core.support.error.UnknownFacilityTypeException
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
        val facilityType: FacilityType = FacilityType.entries.find { it.cdnType == type }
            ?: throw UnknownFacilityTypeException(type, FacilityType.entries.map { it.cdnType })
        val result: FacilityUploadResult = uploadService.upload(facilityType, file.bytes)
        return FacilityUploadResponse.from(result)
    }
}
