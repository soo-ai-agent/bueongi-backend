package io.soo.bueongi.core.domain.admin

import io.soo.bueongi.core.api.controller.v1.FacilityUploadController

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.domain.facility.csv.FacilityCsvParser
import io.soo.bueongi.core.domain.facility.csv.FacilityUploadService
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilityUploadStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart
import java.nio.file.Files

@WebMvcTest(FacilityUploadController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FacilityUploadControllerTest.TestConfig::class)
class FacilityUploadControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    private fun csvFile(content: String) =
        MockMultipartFile("file", "upload.csv", "text/csv", content.toByteArray())

    @Test
    fun `정상 업로드는 200 과 종류·좌표계·집계를 반환한다`() {
        val csv: String = """
            위도,경도,관리기관명,소재지지번주소,설치목적구분,카메라대수
            37.5,127.0,종로구청,서울 종로구 1,생활방범,4
            37.51,127.01,중구청,서울 중구 2,다목적,2
            37.52,127.02,교통과,서울 3,교통단속,1
        """.trimIndent()

        mockMvc.multipart("/api/admin/facilities/cctv/upload") { file(csvFile(csv)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.type") { value("cctv") }
                jsonPath("$.coordSystem") { value("wgs84") }
                jsonPath("$.received") { value(3) }
                jsonPath("$.saved") { value(2) }
                jsonPath("$.skipped") { value(1) }
            }
    }

    @Test
    fun `알 수 없는 종류는 400 UNKNOWN_FACILITY_TYPE`() {
        mockMvc.multipart("/api/admin/facilities/nope/upload") { file(csvFile("위도,경도\n37.5,127.0")) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("UNKNOWN_FACILITY_TYPE") }
                jsonPath("$.errors[0].field") { value("type") }
                jsonPath("$.errors[0].rejectedValue") { value("nope") }
            }
    }

    @Test
    fun `유효 0건(좌표 없음)은 400 VALIDATION_FAILED`() {
        val csv: String = """
            관서명,소재지도로명주소,전화번호
            종로파출소,서울 종로구 대로 8,02-100
        """.trimIndent()

        mockMvc.multipart("/api/admin/facilities/police/upload") { file(csvFile(csv)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("file") }
            }
    }

    @Test
    fun `file 파트가 없으면 400 VALIDATION_FAILED`() {
        mockMvc.multipart("/api/admin/facilities/cctv/upload") { }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("file") }
            }
    }

    @TestConfiguration
    class TestConfig {
        private class FakeLiveStore : FacilityLiveStore {
            override fun replace(type: FacilityType, points: List<FacilityPoint>): Int = points.size
        }

        @Bean
        fun facilityUploadService(): FacilityUploadService {
            val dir: String = Files.createTempDirectory("fac-upload-controller-test").toFile().absolutePath
            return FacilityUploadService(
                parser = FacilityCsvParser(),
                uploadStore = FacilityUploadStore(ObjectMapper(), dir),
                liveStore = FakeLiveStore(),
                refreshStatusStore = null,
            )
        }
    }
}
