package io.soo.bueongi.core.safetyspot.api.controller

import io.soo.bueongi.core.safetyspot.api.request.toValidatedSafetySpotType
import io.soo.bueongi.core.safetyspot.api.response.SafetySpotUploadResponse

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.csv.model.SafetySpotUploadResult
import io.soo.bueongi.core.safetyspot.domain.csv.service.SafetySpotUploadService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/admin/safety-spots")
class SafetySpotUploadController(
    private val uploadService: SafetySpotUploadService,
) {
    @PostMapping("/{type}/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable type: String,
        @RequestParam("file") file: MultipartFile,
    ): SafetySpotUploadResponse {
        val safetySpotType: SafetySpotType = toValidatedSafetySpotType(type)
        val result: SafetySpotUploadResult = uploadService.upload(safetySpotType, file.bytes)
        return SafetySpotUploadResponse.from(result)
    }
}
