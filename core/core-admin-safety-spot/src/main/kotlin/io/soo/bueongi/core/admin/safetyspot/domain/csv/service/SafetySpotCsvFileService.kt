package io.soo.bueongi.core.admin.safetyspot.domain.csv.service

import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotCsvFileResponse
import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotCsvPageResponse
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvSpec
import io.soo.bueongi.core.admin.safetyspot.domain.error.InvalidCsvPageException
import io.soo.bueongi.core.admin.safetyspot.domain.error.SafetySpotCsvFileNotFoundException
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvPage
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotCsvFile
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotCsvFileStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotCsvOrigin
import org.springframework.stereotype.Service

@Service
class SafetySpotCsvFileService(
    private val fileStore: SafetySpotCsvFileStore,
    private val parser: SafetySpotCsvParser,
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
        // 목록에 있는 파일만 연다 — 요청 값으로 경로를 만들지 않아야 data 밖 파일(../ 등)을 열 수 없다.
        var file: SafetySpotCsvFile? = null
        for (listed in fileStore.list()) {
            if (listed.origin == origin && listed.name == name) {
                file = listed
                break
            }
        }
        if (file == null) {
            throw SafetySpotCsvFileNotFoundException(name)
        }
        val csvPage: CsvPage =
            when (file.origin) {
                SafetySpotCsvOrigin.SOURCE -> fileStore.readSourcePage(file, page, size, skippedOnly, keyword)
                SafetySpotCsvOrigin.UPLOAD -> {
                    val bytes: ByteArray = fileStore.readBytes(file)
                    parser.readPage(bytes, SafetySpotCsvSpec.forType(file.type), page, size, skippedOnly, keyword)
                }
            }
        return SafetySpotCsvPageResponse.from(csvPage)
    }

    private companion object {
        const val MAX_PAGE_SIZE: Int = 200
    }
}
