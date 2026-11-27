package io.soo.bueongi.core.admin.safetyspot.csv

import io.soo.bueongi.core.admin.safetyspot.api.response.CsvRowStatus
import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotCsvPageResponse
import io.soo.bueongi.core.admin.safetyspot.domain.csv.service.SafetySpotCsvFileService
import io.soo.bueongi.core.admin.safetyspot.domain.csv.service.SafetySpotCsvReader
import io.soo.bueongi.core.admin.safetyspot.domain.error.InvalidCsvPageException
import io.soo.bueongi.core.admin.safetyspot.domain.error.SafetySpotCsvFileNotFoundException
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.store.CsvSkipReason
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotCsvFileStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotCsvOrigin
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotUploadStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class SafetySpotCsvFileServiceTest {
    @Test
    fun `서버 원본 gz CSV 도 업로드와 같은 판정으로 행을 보여 준다`(
        @TempDir tmp: File,
    ) {
        val csv =
            "WGS84위도,WGS84경도,관리기관명,설치목적구분\n" +
                "37.5,127.0,\"종로구청(본관, 1층)\",생활방범\n37.5,127.0,교통과,교통단속\n"
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { stream -> stream.write(csv.toByteArray()) }
        val gz = File(tmp, "CCTV정보.csv.gz")
        gz.writeBytes(buffer.toByteArray())
        val uploadStore = SafetySpotUploadStore(File(tmp, "uploads").path)
        val none: String = File(tmp, "none").path
        val fileStore = SafetySpotCsvFileStore(uploadStore, gz.path, none, none, none)
        val service = SafetySpotCsvFileService(fileStore, SafetySpotCsvReader())

        val page: SafetySpotCsvPageResponse = service.readPage(SafetySpotCsvOrigin.SOURCE, "CCTV정보.csv.gz", 0, 50, false, "")

        assertThat(page.rows[0].status).isEqualTo(CsvRowStatus.LOADED)
        assertThat(page.rows[0].cells[2]).isEqualTo("종로구청(본관, 1층)")
        assertThat(page.rows[1].status).isEqualTo(CsvRowStatus.SKIPPED)
        assertThat(page.rows[1].skipReason).isEqualTo(CsvSkipReason.NOT_SECURITY_PURPOSE)
    }

    @Test
    fun `업로드 원본은 업로드 때와 같은 판정으로 행을 보여 준다`(
        @TempDir tmp: File,
    ) {
        val uploadStore = SafetySpotUploadStore(File(tmp, "uploads").path)
        val csv = "위도,경도,관리기관명,설치목적구분\n37.5,127.0,종로구청,생활방범\n37.5,127.0,교통과,교통단속\n"
        uploadStore.saveOriginal(SafetySpotType.CCTV, csv.toByteArray())

        val page: SafetySpotCsvPageResponse = service(tmp, uploadStore).readPage(SafetySpotCsvOrigin.UPLOAD, "cctv.csv", 0, 50, false, "")

        assertThat(page.header).containsExactly("위도", "경도", "관리기관명", "설치목적구분")
        assertThat(page.rows[0].status).isEqualTo(CsvRowStatus.LOADED)
        assertThat(page.rows[1].status).isEqualTo(CsvRowStatus.SKIPPED)
        assertThat(page.rows[1].skipReason).isEqualTo(CsvSkipReason.NOT_SECURITY_PURPOSE)
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

    @Test
    fun `목록에 없는 이름은 경로처럼 보여도 열지 않는다`(
        @TempDir tmp: File,
    ) {
        val uploadStore = SafetySpotUploadStore(File(tmp, "uploads").path)
        uploadStore.saveOriginal(SafetySpotType.CCTV, "위도,경도\n37.5,127.0\n".toByteArray())

        assertThatThrownBy { service(tmp, uploadStore).readPage(SafetySpotCsvOrigin.UPLOAD, "../uploads/cctv.csv", 0, 50, false, "") }
            .isInstanceOf(SafetySpotCsvFileNotFoundException::class.java)
    }

    private fun service(
        tmp: File,
        uploadStore: SafetySpotUploadStore = SafetySpotUploadStore(File(tmp, "uploads").path),
    ): SafetySpotCsvFileService {
        val none: String = File(tmp, "none").path
        val fileStore = SafetySpotCsvFileStore(uploadStore, "", none, none, none)
        return SafetySpotCsvFileService(fileStore, SafetySpotCsvReader())
    }
}
