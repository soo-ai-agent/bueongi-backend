package io.soo.bueongi.storage.db.core.safetyspot

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvPage
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvRowResult
import io.soo.bueongi.storage.db.core.safetyspot.store.CsvSkipReason
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotCsvFile
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotCsvFileStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotCsvOrigin
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotUploadStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SafetySpotCsvFileStoreTest {
    private val cctvHeader: String =
        "개방자치단체코드,관리번호,관리기관명,소재지도로명주소,소재지지번주소,설치목적구분," +
            "카메라대수,카메라화소수,촬영방면정보,보관일수,설치연월,관리기관전화번호,WGS84위도,WGS84경도," +
            "데이터기준일자,데이터갱신구분,데이터갱신시점,최종수정시점"

    private fun cctvRow(purpose: String, lat: String): String =
        "3000000,id,종로구청,\"서울 종로구 북촌로 1 (가회동, 1층)\",,$purpose,2,200,전방,30,202005,02-0," +
            "$lat,126.98,2026-05-18,,2026-05-19 22:58:23,2026-05-18 15:24:18"

    @Test
    fun `CCTV 원천은 행마다 적재 여부와 빠진 이유를 낸다`(
        @TempDir tmp: File,
    ) {
        val cctv: File = write(tmp, "cctv.csv", cctvHeader, cctvRow("생활방범", "37.58"), cctvRow("교통단속", "37.58"), cctvRow("생활방범", ""))
        val store: SafetySpotCsvFileStore = store(tmp, cctvCsv = cctv.path)

        val page: CsvPage = store.readSourcePage(store.find(SafetySpotCsvOrigin.SOURCE, "cctv.csv")!!, 0, 10, false, "")

        assertThat(page.rows[0].result).isInstanceOf(CsvRowResult.Loaded::class.java)
        assertThat(page.rows[1].result).isEqualTo(CsvRowResult.Skipped(CsvSkipReason.NOT_SECURITY_PURPOSE))
        assertThat(page.rows[2].result).isEqualTo(CsvRowResult.Skipped(CsvSkipReason.NO_COORDINATE))
        assertThat(page.loadedRows).isEqualTo(1)
        assertThat(page.skippedRows).isEqualTo(2)
    }

    @Test
    fun `따옴표 안의 콤마는 칸을 나누지 않는다`(
        @TempDir tmp: File,
    ) {
        val cctv: File = write(tmp, "cctv.csv", cctvHeader, cctvRow("생활방범", "37.58"))
        val store: SafetySpotCsvFileStore = store(tmp, cctvCsv = cctv.path)

        val page: CsvPage = store.readSourcePage(store.find(SafetySpotCsvOrigin.SOURCE, "cctv.csv")!!, 0, 10, false, "")

        assertThat(page.header).hasSize(18)
        assertThat(page.rows[0].cells).hasSize(18)
        assertThat(page.rows[0].cells[3]).isEqualTo("서울 종로구 북촌로 1 (가회동, 1층)")
    }

    @Test
    fun `서버가 읽지 않는 비상벨 원천은 행을 판정하지 않는다`(
        @TempDir tmp: File,
    ) {
        val bellDir = File(tmp, "bell")
        write(bellDir, "bell.csv", "설치장소,X좌표,Y좌표", "공원,200000,450000")
        val store: SafetySpotCsvFileStore = store(tmp, bellDir = bellDir.path)
        val bell: SafetySpotCsvFile = store.find(SafetySpotCsvOrigin.SOURCE, "bell.csv")!!

        val page: CsvPage = store.readSourcePage(bell, 0, 10, false, "")

        assertThat(bell.readByServer).isFalse()
        assertThat(page.rows.single().result).isEqualTo(CsvRowResult.NotRead)
    }

    @Test
    fun `위도·경도 머리글이 없는 보안등 원천은 모든 행이 좌표 칸 없음으로 빠진다`(
        @TempDir tmp: File,
    ) {
        val lampDir = File(tmp, "lamp")
        write(lampDir, "lamp.csv", "보안등위치명,주소", "골목1,서울")
        val store: SafetySpotCsvFileStore = store(tmp, lampDir = lampDir.path)

        val page: CsvPage = store.readSourcePage(store.find(SafetySpotCsvOrigin.SOURCE, "lamp.csv")!!, 0, 10, false, "")

        assertThat(page.rows.single().result).isEqualTo(CsvRowResult.Skipped(CsvSkipReason.NO_COORDINATE_COLUMNS))
    }

    @Test
    fun `업로드 원본은 업로드로 나오고 같은 종류 원천은 업로드로 대체됐다고 표시된다`(
        @TempDir tmp: File,
    ) {
        val cctv: File = write(tmp, "cctv.csv", cctvHeader)
        val uploadStore = SafetySpotUploadStore(ObjectMapper(), File(tmp, "uploads").path)
        uploadStore.save(SafetySpotType.CCTV, emptyList())
        uploadStore.saveOriginal(SafetySpotType.CCTV, "a,b\n".toByteArray())
        val store: SafetySpotCsvFileStore = store(tmp, cctvCsv = cctv.path, uploadStore = uploadStore)

        val files: List<SafetySpotCsvFile> = store.list()

        assertThat(files.map { Triple(it.origin, it.name, it.replacedByUpload) }).containsExactly(
            Triple(SafetySpotCsvOrigin.SOURCE, "cctv.csv", true),
            Triple(SafetySpotCsvOrigin.UPLOAD, "cctv.csv", false),
        )
    }

    @Test
    fun `목록에 없는 이름은 경로처럼 보여도 찾지 않는다`(
        @TempDir tmp: File,
    ) {
        val cctv: File = write(tmp, "cctv.csv", cctvHeader)
        val store: SafetySpotCsvFileStore = store(tmp, cctvCsv = cctv.path)

        assertThat(store.find(SafetySpotCsvOrigin.SOURCE, "../cctv.csv")).isNull()
    }

    private fun store(
        tmp: File,
        cctvCsv: String = "",
        lampDir: String = File(tmp, "none-lamp").path,
        bellDir: String = File(tmp, "none-bell").path,
        uploadStore: SafetySpotUploadStore = SafetySpotUploadStore(ObjectMapper(), File(tmp, "uploads").path),
    ): SafetySpotCsvFileStore = SafetySpotCsvFileStore(uploadStore, cctvCsv, lampDir, bellDir, File(tmp, "none-police").path)

    private fun write(dir: File, name: String, vararg lines: String): File {
        dir.mkdirs()
        val file = File(dir, name)
        file.writeText(lines.joinToString("\n"))
        return file
    }
}
