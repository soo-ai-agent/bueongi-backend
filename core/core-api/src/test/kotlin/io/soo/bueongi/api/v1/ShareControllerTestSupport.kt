package io.soo.bueongi.api.v1

import io.soo.bueongi.domain.share.MutableTestClock
import io.soo.bueongi.domain.share.ShareService
import io.soo.bueongi.storage.db.core.share.InMemoryLocationStore
import io.soo.bueongi.storage.db.core.share.LocationStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * [ShareController] 슬라이스 테스트(@WebMvcTest)의 공용 토대. MVP 위치 공유 엔드포인트의 HTTP 계약을
 * 실제 [ShareService] + [InMemoryLocationStore] + 가변 Clock 으로 결정론적으로 검증한다(공통
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 슬라이스에 포함됨). 관심사별 하위 클래스가 이 토대를 상속한다:
 * 생성([ShareControllerCreateTest]) · 위치/종료([ShareControllerLocationTest]) ·
 * 보호자 페이지([ShareControllerGuardianPageTest]) · 시청 상태([ShareControllerWatchingTest]) ·
 * 경로([ShareControllerRouteTest]).
 */
@WebMvcTest(ShareController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ShareControllerTestSupport.ShareTestConfig::class)
@ActiveProfiles("test")
abstract class ShareControllerTestSupport {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    /** 가변 Clock — 만료 경계를 전진시키는 테스트가 주입받는다. */
    @Autowired
    protected lateinit var clock: MutableTestClock

    /** POST 로 공유를 만들고 token(읽기 전용)을 돌려준다(조회 전용 테스트 헬퍼). */
    protected fun createShare(body: String = "{}"): String {
        val response: String = mockMvc.post("/share/create") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().response.contentAsString
        return Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]
    }

    /** 공유를 만들고 (token, owner_secret) 둘 다 돌려준다(위치 쓰기/종료 테스트 헬퍼). */
    protected fun createSharePair(body: String = "{}"): Pair<String, String> {
        val response: String = mockMvc.post("/share/create") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().response.contentAsString
        val token: String = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]
        val secret: String = Regex("\"owner_secret\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]
        return token to secret
    }

    @TestConfiguration
    class ShareTestConfig {
        /** 가변 Clock — 테스트가 주입받아 만료 경계를 전진시킨다. */
        @Bean
        fun clock(): MutableTestClock = MutableTestClock()

        @Bean
        fun locationStore(clock: MutableTestClock): LocationStore = InMemoryLocationStore(clock)

        @Bean
        fun shareService(store: LocationStore, clock: MutableTestClock): ShareService =
            ShareService(store, clock, "http://localhost:8119")
    }
}
