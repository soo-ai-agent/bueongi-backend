package io.soo.bueongi.api

import io.soo.bueongi.core.place.domain.service.RemotePlaceProvider
import io.soo.bueongi.core.share.domain.service.ShareService
import java.time.Instant
import java.util.Properties
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

class AppInfoControllerTest {

    @WebMvcTest(AppInfoController::class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(AppInfoDefaultTest.Config::class)
    @ActiveProfiles("test")
    class AppInfoDefaultTest(
        @param:Autowired private val mockMvc: MockMvc,
    ) {
        @Test
        fun `프론트 버전 미설정이면 빈 문자열이고 서버 버전·상태는 채워진다`() {
            mockMvc.get("/api/app-info")
                .andExpect {
                    status { isOk() }
                    content { contentTypeCompatibleWith("application/json") }
                    jsonPath("$.serverStatus") { value("UP") }
                    jsonPath("$.serverVersion") { value("1.2.3") }
                    jsonPath("$.serverBuiltAt") { value("2026-08-17T00:00:00Z") }
                    jsonPath("$.frontendVersion") { value("") }
                    jsonPath("$.placeSource") { value("seed") }
                    jsonPath("$.locationStore") { value("memory") }
                }
        }

        @TestConfiguration
        class Config {
            @Bean
            fun remotePlaceProvider(): RemotePlaceProvider = stubRemotePlaceProvider(enabled = false)

            @Bean
            fun shareService(): ShareService = stubShareService()

            @Bean
            fun buildProperties(): BuildProperties = buildProperties("1.2.3", "2026-08-17T00:00:00Z")
        }
    }

    @WebMvcTest(AppInfoController::class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(AppInfoConfiguredTest.Config::class)
    @ActiveProfiles("test")
    @TestPropertySource(properties = ["bueongi.app-info.frontend-version=2.4.0"])
    class AppInfoConfiguredTest(
        @param:Autowired private val mockMvc: MockMvc,
    ) {
        @Test
        fun `프론트 버전을 설정하면 그 값이 내려간다`() {
            mockMvc.get("/api/app-info")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.frontendVersion") { value("2.4.0") }
                }
        }

        @TestConfiguration
        class Config {
            @Bean
            fun remotePlaceProvider(): RemotePlaceProvider = stubRemotePlaceProvider(enabled = false)

            @Bean
            fun shareService(): ShareService = stubShareService()
        }
    }
}

private fun buildProperties(version: String, builtAt: String): BuildProperties {
    val entries = Properties()
    entries["version"] = version
    entries["time"] = Instant.parse(builtAt).toEpochMilli().toString()
    return BuildProperties(entries)
}
