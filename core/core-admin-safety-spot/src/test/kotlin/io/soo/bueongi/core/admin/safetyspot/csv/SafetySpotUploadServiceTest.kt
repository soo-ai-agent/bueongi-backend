package io.soo.bueongi.core.admin.safetyspot.csv

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.admin.safetyspot.domain.csv.service.SafetySpotCsvParser
import io.soo.bueongi.core.admin.safetyspot.domain.csv.service.SafetySpotUploadService
import io.soo.bueongi.core.admin.safetyspot.domain.error.EmptySafetySpotUploadException
import io.soo.bueongi.core.enums.SafetySpotCoordSystem
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotUploadStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SafetySpotUploadServiceTest {
    private val mapper = ObjectMapper()

    private class FakeLiveStore : SafetySpotLiveStore {
        var lastType: SafetySpotType? = null
        var lastPoints: List<SafetySpotPoint>? = null

        override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {}

        override fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>) {
            lastType = type
            lastPoints = points
        }
    }

    private fun service(tmp: Path, liveStore: SafetySpotLiveStore, statusStore: SafetySpotRefreshStatusStore) =
        SafetySpotUploadService(
            parser = SafetySpotCsvParser(),
            uploadStore = SafetySpotUploadStore(mapper, tmp.toFile().absolutePath),
            liveStore = liveStore,
            refreshStatusStore = statusStore,
        )

    @Test
    fun `업로드는 파일 저장 + 라이브 교체 + 상태 기록을 하고 집계를 반환한다`(
        @TempDir tmp: Path,
    ) {
        val liveStore = FakeLiveStore()
        val statusStore = SafetySpotRefreshStatusStore()
        val csv =
            """
            위도,경도,관리기관명,소재지지번주소,설치목적구분,카메라대수
            37.5,127.0,종로구청,서울 종로구 1,생활방범,4
            37.51,127.01,중구청,서울 중구 2,다목적,2
            37.52,127.02,교통과,서울 3,교통단속,1
            """.trimIndent()

        val result = service(tmp, liveStore, statusStore).upload(SafetySpotType.CCTV, csv.toByteArray())

        assertThat(result.type).isEqualTo(SafetySpotType.CCTV)
        assertThat(result.coordSystem).isEqualTo(SafetySpotCoordSystem.WGS84)
        assertThat(result.received).isEqualTo(3)
        assertThat(result.saved).isEqualTo(2)
        assertThat(result.skipped).isEqualTo(1)

        assertThat(File(tmp.toFile(), "cctv.json")).exists()
        assertThat(liveStore.lastType).isEqualTo(SafetySpotType.CCTV)
        assertThat(liveStore.lastPoints).hasSize(2)
        val record = statusStore.findByType(SafetySpotType.CCTV)!!
        assertThat(record.replacedCount).isEqualTo(2)
    }

    @Test
    fun `업로드는 올린 CSV 원본을 그대로 보관한다`(
        @TempDir tmp: Path,
    ) {
        val csv = "위도,경도,관리기관명,설치목적구분\n37.5,127.0,종로구청,생활방범\n"

        service(tmp, FakeLiveStore(), SafetySpotRefreshStatusStore()).upload(SafetySpotType.CCTV, csv.toByteArray())

        assertThat(File(tmp.toFile(), "originals/cctv.csv")).hasContent(csv)
    }

    @Test
    fun `유효 0건이면 교체하지 않고 EmptySafetySpotUploadException`(
        @TempDir tmp: Path,
    ) {
        val liveStore = FakeLiveStore()
        val csv =
            """
            관서명,소재지도로명주소,전화번호
            종로파출소,서울 종로구 대로 8,02-100
            """.trimIndent()

        assertThatThrownBy { service(tmp, liveStore, SafetySpotRefreshStatusStore()).upload(SafetySpotType.POLICE, csv.toByteArray()) }
            .isInstanceOf(EmptySafetySpotUploadException::class.java)

        assertThat(liveStore.lastPoints).isNull()
        assertThat(File(tmp.toFile(), "police.json")).doesNotExist()
    }
}
