package io.soo.bueongi.core.admin.safetyspot.domain.csv.service

import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotCsvFileResponse
import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotCsvPageResponse
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvFormat
import io.soo.bueongi.core.admin.safetyspot.domain.error.InvalidCsvPageException
import io.soo.bueongi.core.admin.safetyspot.domain.error.SafetySpotCsvFileNotFoundException
import io.soo.bueongi.storage.db.safetyspot.store.CsvPage
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotCsvFile
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotCsvFileStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotCsvOrigin
import org.springframework.stereotype.Service

@Service
class SafetySpotCsvFileService(
    private val fileStore: SafetySpotCsvFileStore,
    private val reader: SafetySpotCsvReader,
) {
    fun list(): List<SafetySpotCsvFileResponse> {
        val files: List<SafetySpotCsvFile> = fileStore.list()
        return SafetySpotCsvFileResponse.from(files)
    }

    fun readPage(
        origin: SafetySpotCsvOrigin,
        name: String,
        page: Int,
        size: Int,
        skippedOnly: Boolean,
        keyword: String,
    ): SafetySpotCsvPageResponse {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw InvalidCsvPageException(page, size, MAX_PAGE_SIZE)
        }
        val file: SafetySpotCsvFile = getListedFile(origin, name)
        val bytes: ByteArray = fileStore.readBytes(file)
        val format: SafetySpotCsvFormat = SafetySpotCsvFormat.forType(file.type)
        val csvPage: CsvPage = reader.readPage(bytes, format, page, size, skippedOnly, keyword)
        return SafetySpotCsvPageResponse.from(csvPage)
    }

    // 목록에 있는 파일만 연다 — 요청 값으로 경로를 만들지 않아야 data 밖 파일(../ 등)을 열 수 없다.
    private fun getListedFile(origin: SafetySpotCsvOrigin, name: String): SafetySpotCsvFile {
        for (file in fileStore.list()) {
            if (file.origin == origin && file.name == name) {
                return file
            }
        }
        throw SafetySpotCsvFileNotFoundException(name)
    }

    private companion object {
        const val MAX_PAGE_SIZE: Int = 200
    }
}
