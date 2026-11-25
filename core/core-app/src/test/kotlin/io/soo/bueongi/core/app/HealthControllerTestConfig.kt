package io.soo.bueongi.core.app

import io.soo.bueongi.core.share.domain.service.ShareService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.time.Clock

@TestConfiguration
class HealthControllerTestConfig {
    @Bean
    fun shareService(): ShareService = ShareService(StubShareSessionStore("memory"), Clock.systemUTC(), "http://localhost:3619")
}
