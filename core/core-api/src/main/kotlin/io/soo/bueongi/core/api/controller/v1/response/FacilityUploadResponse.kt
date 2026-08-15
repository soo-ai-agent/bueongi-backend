package io.soo.bueongi.core.api.controller.v1.response

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.enums.FacilityCoordSystem
import io.soo.bueongi.core.domain.facility.FacilityUploadResult
import io.soo.bueongi.core.domain.facility.FacilityUploadService
import io.soo.bueongi.core.support.error.UnknownFacilityTypeException
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

data class FacilityUploadResponse(
    val type: FacilityType,
    val coordSystem: FacilityCoordSystem,
    val received: Int,
    val saved: Int,
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
