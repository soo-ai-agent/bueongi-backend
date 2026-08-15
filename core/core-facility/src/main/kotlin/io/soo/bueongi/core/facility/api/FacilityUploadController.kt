package io.soo.bueongi.core.facility.api

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
        @PathVariable type: FacilityType,
        @RequestParam("file") file: MultipartFile,
    ): FacilityUploadResponse {
        val result: FacilityUploadResult = uploadService.upload(type, file.bytes)
        return FacilityUploadResponse.from(result)
    }
}
