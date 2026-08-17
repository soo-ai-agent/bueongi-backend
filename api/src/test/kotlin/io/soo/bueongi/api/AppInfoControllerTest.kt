package io.soo.bueongi.api

import io.soo.bueongi.core.place.domain.model.PlaceItem
import io.soo.bueongi.core.place.domain.service.RemotePlaceProvider
import io.soo.bueongi.core.share.domain.service.ShareService
import io.soo.bueongi.storage.db.core.share.model.ShareSession
import io.soo.bueongi.storage.db.core.share.store.LocationStore
import java.time.Clock
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
            fun remotePlaceProvider(): RemotePlaceProvider = seedOnlyProvider()

            @Bean
            fun shareService(): ShareService = appInfoStubShareService()

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
            fun remotePlaceProvider(): RemotePlaceProvider = seedOnlyProvider()

            @Bean
            fun shareService(): ShareService = appInfoStubShareService()
        }
    }
}

private fun seedOnlyProvider(): RemotePlaceProvider = object : RemotePlaceProvider {
    override val isEnabled: Boolean = false
    override fun search(keyword: String): List<PlaceItem> =
        throw AssertionError("app-info 는 search 를 호출하지 않는다")
}

private fun buildProperties(version: String, builtAt: String): BuildProperties {
    val entries = Properties()
    entries["version"] = version
    entries["time"] = Instant.parse(builtAt).toEpochMilli().toString()
    return BuildProperties(entries)
}

private fun appInfoStubShareService(): ShareService =
    ShareService(AppInfoStubLocationStore("memory"), Clock.systemUTC(), "http://localhost:3619")

private class AppInfoStubLocationStore(override val backend: String) : LocationStore {
    override fun save(session: ShareSession) = throw AssertionError("app-info 는 save 를 호출하지 않는다")
    override fun findByToken(token: String): ShareSession? = throw AssertionError("app-info 는 findByToken 을 호출하지 않는다")
    override fun deleteByToken(token: String) = throw AssertionError("app-info 는 delete 를 호출하지 않는다")
    override fun markViewed(token: String) = throw AssertionError("app-info 는 markViewed 를 호출하지 않는다")
    override fun isWatched(token: String): Boolean = throw AssertionError("app-info 는 isWatched 를 호출하지 않는다")
    override fun countActiveSessions(): Int = throw AssertionError("app-info 는 countActiveSessions 를 호출하지 않는다")
    override fun findActiveSessions(): List<ShareSession> = throw AssertionError("app-info 는 findActiveSessions 를 호출하지 않는다")
}
