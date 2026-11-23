package io.soo.bueongi.core.admin.safetyspot.api.controller

import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotCsvFileResponse
import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotCsvPageResponse
import io.soo.bueongi.core.admin.safetyspot.domain.csv.service.SafetySpotCsvFileService
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotCsvOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/safety-spot-csv-files")
class SafetySpotCsvFileController(
    private val csvFileService: SafetySpotCsvFileService,
) {
    @GetMapping
    fun list(): List<SafetySpotCsvFileResponse> {
        return csvFileService.list()
    }

    @GetMapping("/rows")
    fun readPage(
        @RequestParam origin: SafetySpotCsvOrigin,
        @RequestParam name: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "false") skippedOnly: Boolean,
        @RequestParam(defaultValue = "") keyword: String,
    ): SafetySpotCsvPageResponse {
        return csvFileService.readPage(origin, name, page, size, skippedOnly, keyword)
    }
}
