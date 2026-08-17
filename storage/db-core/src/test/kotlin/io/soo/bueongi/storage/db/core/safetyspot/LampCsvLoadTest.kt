package io.soo.bueongi.storage.db.core.safetyspot

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRepository
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotUploadStore
import java.io.File
import java.nio.charset.Charset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LampCsvLoadTest {

    private val header: String =
        "보안등위치명,설치개수,소재지도로명주소,소재지지번주소,위도,경도,설치연도,설치형태," +
            "관리기관전화번호,관리기관명,데이터기준일자,제공기관코드,제공기관명"

    @Test
    fun `좌표가 빈 행은 건너뛰고 좌표가 있는 행만 적재한다`(@TempDir tmp: File) {
        val csvDir = csvDirOf(
            tmp,
            header,
            "창녕3219,1,,경남 창녕군,35.5443,128.4923,,,055-000,창녕군청,2026-06-26,5410000,경상남도 창녕군",
            "도화동 07-08,1,서울 마포구 토정로37길 46,,,,,전용주,02-000,마포구청,2026-06-26,3130000,서울특별시 마포구",
        )

        val points = loadLamp(tmp, csvDir)

        assertThat(points).hasSize(1)
        assertThat(points[0].name).isEqualTo("창녕3219")
        assertThat(points[0].lat).isEqualTo(35.5443)
    }

    @Test
    fun `헤더 순서가 바뀌어도 컬럼 이름으로 찾는다`(@TempDir tmp: File) {
        val csvDir = csvDirOf(tmp, "경도,위도,보안등위치명", "128.4923,35.5443,창녕3219")

        val points = loadLamp(tmp, csvDir)

        assertThat(points).hasSize(1)
        assertThat(points[0].lng).isEqualTo(128.4923)
    }

    @Test
    fun `위도 경도 컬럼이 없으면 적재하지 않는다`(@TempDir tmp: File) {
        val csvDir = csvDirOf(tmp, "보안등위치명,주소", "창녕3219,경남 창녕군")

        assertThat(loadLamp(tmp, csvDir)).isEmpty()
    }

    /** 적재기는 폴더 안 CSV 를 전부 읽으므로 파일이 아니라 폴더를 만든다. */
    private fun csvDirOf(tmp: File, vararg lines: String): File {
        val dir = File(tmp, "lamp").apply { mkdirs() }
        File(dir, "lamp.csv").writeText(lines.joinToString("\n"), CP949)
        return dir
    }

    /** 적재 결과는 좌표(창녕 근처)로 조회해 확인한다 — 인덱스는 반경 조회만 열려 있다. */
    private fun loadLamp(tmp: File, csvDir: File): List<SafetySpotPoint> {
        val uploads = File(tmp, "uploads").apply { mkdirs() }
        val repo = SafetySpotRepository(
            "",
            ObjectMapper(),
            SafetySpotUploadStore(ObjectMapper(), uploads.absolutePath),
            "",
            csvDir.path,
        )
        repo.load()
        return repo.index(SafetySpotType.SECURITY_LAMP).withinRadius(GeoPoint(35.5443, 128.4923), 2_000.0)
    }

    private companion object {
        val CP949: Charset = Charset.forName("MS949")
    }
}
