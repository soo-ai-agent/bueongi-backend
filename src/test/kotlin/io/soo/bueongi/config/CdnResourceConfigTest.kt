package io.soo.bueongi.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.nio.file.Files
import java.nio.file.Path

/**
 * [CdnResourceConfig] — 시설 데이터 디렉터리(`bueongi.facility.data-dir`)의 `/cdn` 하위 HTTP 서빙 계약.
 *
 * 배포에서 프론트의 `VITE_CDN_BASE_URL` 이 이 경로(nginx → 백엔드 프록시)를 가리키므로,
 * ① 파일이 그대로 내려오고 ② 없는 파일은 404 이며 ③ 월 1회 갱신 데이터답게 캐시 헤더가 붙는지 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CdnResourceConfigTest(
    @Autowired private val mockMvc: MockMvc,
) {

    companion object {
        private val cdnDir: Path = Files.createTempDirectory("bueongi-cdn-test").also {
            Files.createDirectories(it.resolve("police"))
            Files.writeString(it.resolve("police/all.json"), """{"items":[{"id":"p1"}]}""")
            Files.writeString(it.resolve("manifest.json"), """{"updated":"2026-07"}""")
        }

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("bueongi.facility.data-dir") { cdnDir.toString() }
        }
    }

    @Test
    fun `데이터 디렉터리의 JSON 을 하위 경로 포함 그대로 서빙한다`() {
        mockMvc.get("/cdn/police/all.json").andExpect {
            status { isOk() }
            content { json("""{"items":[{"id":"p1"}]}""") }
        }
        mockMvc.get("/cdn/manifest.json").andExpect {
            status { isOk() }
            content { json("""{"updated":"2026-07"}""") }
        }
    }

    @Test
    fun `월 1회 갱신 데이터 - public 캐시 헤더를 붙인다`() {
        mockMvc.get("/cdn/manifest.json").andExpect {
            status { isOk() }
            header { string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=1800")) }
            header { string("Cache-Control", org.hamcrest.Matchers.containsString("public")) }
        }
    }

    @Test
    fun `없는 파일은 404 로 정직하게 응답한다`() {
        mockMvc.get("/cdn/없는파일.json").andExpect {
            status { isNotFound() }
        }
    }
}
