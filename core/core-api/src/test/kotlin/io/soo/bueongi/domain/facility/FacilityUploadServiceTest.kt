package io.soo.bueongi.domain.facility

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilityUploadStore
import io.soo.bueongi.support.error.EmptyFacilityUploadException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class FacilityUploadServiceTest {

    private val mapper: ObjectMapper = ObjectMapper()

    private class FakeLiveStore : FacilityLiveStore {
        var lastType: FacilityType? = null
        var lastPoints: List<FacilityPoint>? = null
        override fun replace(type: FacilityType, points: List<FacilityPoint>): Int {
            lastType = type
            lastPoints = points
            return points.size
        }
    }

    private fun service(tmp: Path, liveStore: FacilityLiveStore, statusStore: FacilityRefreshStatusStore?) =
        FacilityUploadService(
            parser = FacilityCsvParser(),
            uploadStore = FacilityUploadStore(mapper, tmp.toFile().absolutePath),
            liveStore = liveStore,
            refreshStatusStore = statusStore,
        )

    @Test
    fun `업로드는 파일 저장 + 라이브 교체 + 상태 기록을 하고 집계를 반환한다`(@TempDir tmp: Path) {
        val liveStore: FakeLiveStore = FakeLiveStore()
        val statusStore: FacilityRefreshStatusStore = FacilityRefreshStatusStore()
        val csv = """
            위도,경도,관리기관명,소재지지번주소,설치목적구분,카메라대수
            37.5,127.0,종로구청,서울 종로구 1,생활방범,4
            37.51,127.01,중구청,서울 중구 2,다목적,2
            37.52,127.02,교통과,서울 3,교통단속,1
        """.trimIndent()

        val result = service(tmp, liveStore, statusStore).upload(FacilityType.CCTV, csv.toByteArray())

        assertThat(result.type).isEqualTo(FacilityType.CCTV)
        assertThat(result.coordSystem).isEqualTo(FacilityCoordSystem.WGS84)
        assertThat(result.received).isEqualTo(3)
        assertThat(result.saved).isEqualTo(2)
        assertThat(result.skipped).isEqualTo(1)

        assertThat(File(tmp.toFile(), "cctv.json")).exists()
        assertThat(liveStore.lastType).isEqualTo(FacilityType.CCTV)
        assertThat(liveStore.lastPoints).hasSize(2)
        val record = statusStore.findByType(FacilityType.CCTV)!!
        assertThat(record.replacedCount).isEqualTo(2)
    }

    @Test
    fun `유효 0건이면 교체하지 않고 EmptyFacilityUploadException`(@TempDir tmp: Path) {
        val liveStore: FakeLiveStore = FakeLiveStore()
        val csv = """
            관서명,소재지도로명주소,전화번호
            종로파출소,서울 종로구 대로 8,02-100
        """.trimIndent()

        assertThatThrownBy { service(tmp, liveStore, null).upload(FacilityType.POLICE, csv.toByteArray()) }
            .isInstanceOf(EmptyFacilityUploadException::class.java)

        assertThat(liveStore.lastPoints).isNull()
        assertThat(File(tmp.toFile(), "police.json")).doesNotExist()
    }
}
