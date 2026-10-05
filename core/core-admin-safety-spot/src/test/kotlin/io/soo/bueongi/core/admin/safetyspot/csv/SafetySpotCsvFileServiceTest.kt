package io.soo.bueongi.core.admin.safetyspot.csv

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.admin.safetyspot.domain.csv.service.SafetySpotCsvFileService
import io.soo.bueongi.core.admin.safetyspot.domain.csv.service.SafetySpotCsvParser
import io.soo.bueongi.core.admin.safetyspot.domain.error.InvalidCsvPageException
import io.soo.bueongi.core.admin.safetyspot.domain.error.SafetySpotCsvFileNotFoundException
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvPage
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvRowResult
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvSkipReason
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotCsvFileStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotCsvOrigin
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotUploadStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SafetySpotCsvFileServiceTest {
    @Test
    fun `업로드 원본은 업로드 때와 같은 판정으로 행을 보여 준다`(
        @TempDir tmp: File,
    ) {
        val uploadStore = SafetySpotUploadStore(ObjectMapper(), File(tmp, "uploads").path)
        val csv = "위도,경도,관리기관명,설치목적구분\n37.5,127.0,종로구청,생활방범\n37.5,127.0,교통과,교통단속\n"
        uploadStore.saveOriginal(SafetySpotType.CCTV, csv.toByteArray())

        val page: CsvPage = service(tmp, uploadStore).readPage(SafetySpotCsvOrigin.UPLOAD, "cctv.csv", 0, 50, false, "")

        assertThat(page.header).containsExactly("위도", "경도", "관리기관명", "설치목적구분")
        assertThat(page.rows[0].result).isInstanceOf(CsvRowResult.Loaded::class.java)
        assertThat(page.rows[1].result).isEqualTo(CsvRowResult.Skipped(CsvSkipReason.NOT_SECURITY_PURPOSE))
    }

    @Test
    fun `쪽 크기가 범위 밖이면 InvalidCsvPageException`(
        @TempDir tmp: File,
    ) {
        assertThatThrownBy { service(tmp).readPage(SafetySpotCsvOrigin.UPLOAD, "cctv.csv", 0, 201, false, "") }
            .isInstanceOf(InvalidCsvPageException::class.java)
    }

    @Test
    fun `목록에 없는 파일이면 SafetySpotCsvFileNotFoundException`(
        @TempDir tmp: File,
    ) {
        assertThatThrownBy { service(tmp).readPage(SafetySpotCsvOrigin.UPLOAD, "none.csv", 0, 50, false, "") }
            .isInstanceOf(SafetySpotCsvFileNotFoundException::class.java)
    }

    private fun service(
        tmp: File,
        uploadStore: SafetySpotUploadStore = SafetySpotUploadStore(ObjectMapper(), File(tmp, "uploads").path),
    ): SafetySpotCsvFileService {
        val none: String = File(tmp, "none").path
        val fileStore = SafetySpotCsvFileStore(uploadStore, "", none, none, none)
        return SafetySpotCsvFileService(fileStore, SafetySpotCsvParser())
    }
}
