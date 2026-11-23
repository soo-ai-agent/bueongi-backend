package io.soo.bueongi.core.app

import io.soo.bueongi.core.share.MutableTestClock
import io.soo.bueongi.core.share.api.controller.GuardianPage
import io.soo.bueongi.core.share.api.controller.ShareController
import io.soo.bueongi.core.share.domain.service.ShareService
import io.soo.bueongi.storage.db.share.store.InMemoryShareSessionStore
import io.soo.bueongi.storage.db.share.store.ShareSessionStore
import org.junit.jupiter.api.Tag
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

@WebMvcTest(ShareController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ShareControllerTestSupport.ShareTestConfig::class, GuardianPage::class)
@ActiveProfiles("test")
@Tag("context")
abstract class ShareControllerTestSupport {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var clock: MutableTestClock

    protected fun createShare(body: String = "{}"): String {
        val response: String =
            mockMvc.post("/share/create") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andReturn().response.contentAsString
        return Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]
    }

    protected fun createSharePair(body: String = "{}"): Pair<String, String> {
        val response: String =
            mockMvc.post("/share/create") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andReturn().response.contentAsString
        val token: String = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]
        val secret: String = Regex("\"owner_secret\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]
        return token to secret
    }

    @TestConfiguration
    class ShareTestConfig {
        @Bean
        fun clock(): MutableTestClock = MutableTestClock()

        @Bean
        fun locationStore(clock: MutableTestClock): ShareSessionStore = InMemoryShareSessionStore(clock)

        @Bean
        fun shareService(store: ShareSessionStore, clock: MutableTestClock): ShareService =
            ShareService(store, clock, "http://localhost:8119")
    }
}
