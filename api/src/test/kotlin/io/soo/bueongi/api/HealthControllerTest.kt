package io.soo.bueongi.api

import io.soo.bueongi.core.place.domain.service.RemotePlaceProvider
import io.soo.bueongi.core.share.domain.service.ShareService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

class HealthControllerTest {
    @Tag("context")
    @WebMvcTest(HealthController::class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(HealthControllerSeedTest.DisabledRemoteConfig::class)
    @ActiveProfiles("test")
    class HealthControllerSeedTest(
        @param:Autowired private val mockMvc: MockMvc,
    ) {
        @Test
        fun `키 미설정이면 200 과 status UP, placeSource seed, locationStore memory`() {
            mockMvc.get("/api/health")
                .andExpect {
                    status { isOk() }
                    content { contentTypeCompatibleWith("application/json") }
                    jsonPath("$.status") { value("UP") }
                    jsonPath("$.service") { value("bueongi-backend") }
                    jsonPath("$.placeSource") { value("seed") }
                    jsonPath("$.locationStore") { value("memory") }
                }
        }

        @TestConfiguration
        class DisabledRemoteConfig {
            @Bean
            fun remotePlaceProvider(): RemotePlaceProvider = stubRemotePlaceProvider(enabled = false)

            @Bean
            fun shareService(): ShareService = stubShareService()
        }
    }

    @Tag("context")
    @WebMvcTest(HealthController::class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(HealthControllerLiveTest.EnabledRemoteConfig::class)
    @ActiveProfiles("test")
    class HealthControllerLiveTest(
        @param:Autowired private val mockMvc: MockMvc,
    ) {
        @Test
        fun `키 설정이면 placeSource live 이고 키 값은 응답에 노출되지 않는다`() {
            mockMvc.get("/api/health")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value("UP") }
                    jsonPath("$.placeSource") { value("live") }
                    content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(SECRET_KEY))) }
                }
        }

        @TestConfiguration
        class EnabledRemoteConfig {
            @Bean
            fun remotePlaceProvider(): RemotePlaceProvider = stubRemotePlaceProvider(enabled = true)

            @Bean
            fun shareService(): ShareService = stubShareService()
        }

        companion object {
            const val SECRET_KEY: String = "super-secret-kakao-key-value"
        }
    }
}
