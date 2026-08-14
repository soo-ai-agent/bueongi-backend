package io.soo.bueongi.api.v1

import io.soo.bueongi.place.PlaceItem
import io.soo.bueongi.place.RemotePlaceProvider
import io.soo.bueongi.share.ShareService
import io.soo.bueongi.storage.db.core.share.LocationStore
import io.soo.bueongi.storage.db.core.share.ShareSession
import java.time.Clock
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

    @WebMvcTest(HealthController::class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(HealthControllerSeedTest.DisabledRemoteConfig::class)
    @ActiveProfiles("test")
    class HealthControllerSeedTest(
        @Autowired private val mockMvc: MockMvc,
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
            fun remotePlaceProvider(): RemotePlaceProvider = object : RemotePlaceProvider {
                override val isEnabled: Boolean = false
                override fun search(keyword: String): List<PlaceItem> =
                    throw AssertionError("health 는 search 를 호출하지 않는다")
            }

            @Bean
            fun shareService(): ShareService = stubShareService()
        }
    }

    @WebMvcTest(HealthController::class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(HealthControllerLiveTest.EnabledRemoteConfig::class)
    @ActiveProfiles("test")
    class HealthControllerLiveTest(
        @Autowired private val mockMvc: MockMvc,
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
            fun remotePlaceProvider(): RemotePlaceProvider = object : RemotePlaceProvider {
                override val isEnabled: Boolean = true
                override fun search(keyword: String): List<PlaceItem> =
                    throw AssertionError("health 는 search 를 호출하지 않는다")
            }

            @Bean
            fun shareService(): ShareService = stubShareService()
        }

        companion object {
            const val SECRET_KEY: String = "super-secret-kakao-key-value"
        }
    }
}

private fun stubShareService(): ShareService =
    ShareService(StubLocationStore("memory"), Clock.systemUTC(), "http://localhost:3619")

private class StubLocationStore(override val backend: String) : LocationStore {
    override fun save(session: ShareSession) = throw AssertionError("health 는 save 를 호출하지 않는다")
    override fun findByToken(token: String): ShareSession? = throw AssertionError("health 는 findByToken 을 호출하지 않는다")
    override fun deleteByToken(token: String) = throw AssertionError("health 는 delete 를 호출하지 않는다")
    override fun markViewed(token: String) = throw AssertionError("health 는 markViewed 를 호출하지 않는다")
    override fun isWatched(token: String): Boolean = throw AssertionError("health 는 isWatched 를 호출하지 않는다")
    override fun countActiveSessions(): Int = throw AssertionError("health 는 countActiveSessions 를 호출하지 않는다")
    override fun findActiveSessions(): List<ShareSession> = throw AssertionError("health 는 findActiveSessions 를 호출하지 않는다")
}
