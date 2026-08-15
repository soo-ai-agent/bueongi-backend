package io.soo.bueongi.api

import io.soo.bueongi.core.share.MutableTestClock
import io.soo.bueongi.core.share.domain.ShareService
import io.soo.bueongi.core.share.api.GuardianPage
import io.soo.bueongi.core.share.api.ShareController

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

@WebMvcTest(ShareController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ShareControllerTestSupport.ShareTestConfig::class, GuardianPage::class)
@ActiveProfiles("test")
abstract class ShareControllerTestSupport {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var clock: MutableTestClock

    protected fun createShare(body: String = "{}"): String {
        val response: String = mockMvc.post("/share/create") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().response.contentAsString
        return Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]
    }

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
        @Bean
        fun clock(): MutableTestClock = MutableTestClock()

        @Bean
        fun locationStore(clock: MutableTestClock): LocationStore = InMemoryLocationStore(clock)

        @Bean
        fun shareService(store: LocationStore, clock: MutableTestClock): ShareService =
            ShareService(store, clock, "http://localhost:8119")
    }
}
