package io.soo.bueongi.api.v1

import io.soo.bueongi.domain.place.PlaceItem
import io.soo.bueongi.domain.place.RemotePlaceProvider
import io.soo.bueongi.domain.share.ShareService
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

/**
 * [HealthController] 슬라이스 테스트(@WebMvcTest). GET /api/health 의 HTTP 계약(상태 + JSON shape)을 고정하고,
 * 특히 활성 소스 라벨([HealthStatus.placeSource])이 [RemotePlaceProvider.isEnabled] 를 정확히 반영하면서
 * **시크릿(키 값)을 응답에 노출하지 않음**을 검증한다.
 */
class HealthControllerTest {

    /** 외부 키 미설정(remote disabled) → placeSource="seed". */
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
                override val isEnabled = false
                override fun search(keyword: String): List<PlaceItem> =
                    throw AssertionError("health 는 search 를 호출하지 않는다")
            }

            @Bean
            fun shareService(): ShareService = stubShareService()
        }
    }

    /** 외부 키 설정(remote enabled) → placeSource="live", 키 값은 응답 어디에도 없음. */
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
                    // 시크릿 비노출: 응답 본문 어디에도 키 문자열이 없어야 한다.
                    content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(SECRET_KEY))) }
                }
        }

        @TestConfiguration
        class EnabledRemoteConfig {
            @Bean
            fun remotePlaceProvider(): RemotePlaceProvider = object : RemotePlaceProvider {
                override val isEnabled = true
                override fun search(keyword: String): List<PlaceItem> =
                    throw AssertionError("health 는 search 를 호출하지 않는다")
            }

            @Bean
            fun shareService(): ShareService = stubShareService()
        }

        companion object {
            /** 응답에 절대 나타나면 안 되는 가짜 시크릿(노출 회귀 가드용 상수). */
            const val SECRET_KEY = "super-secret-kakao-key-value"
        }
    }
}

/** health 는 [ShareService.storeBackendLabel] 라벨만 쓰므로 스텁 저장소("memory") 위의 실 서비스면 충분하다. */
private fun stubShareService(): ShareService =
    ShareService(StubLocationStore("memory"), Clock.systemUTC(), "http://localhost:3619")

/**
 * health 슬라이스 테스트용 [LocationStore] 스텁. health 는 backend 라벨만 읽으므로
 * 저장 동작은 사용되지 않는다(호출 시 단언 실패로 오용을 드러냄).
 */
private class StubLocationStore(override val backend: String) : LocationStore {
    override fun save(session: ShareSession) = throw AssertionError("health 는 save 를 호출하지 않는다")
    override fun findByToken(token: String): ShareSession? = throw AssertionError("health 는 findByToken 을 호출하지 않는다")
    override fun deleteByToken(token: String) = throw AssertionError("health 는 delete 를 호출하지 않는다")
    override fun markViewed(token: String) = throw AssertionError("health 는 markViewed 를 호출하지 않는다")
    override fun isWatched(token: String): Boolean = throw AssertionError("health 는 isWatched 를 호출하지 않는다")
    override fun countActiveSessions(): Int = throw AssertionError("health 는 countActiveSessions 를 호출하지 않는다")
}
