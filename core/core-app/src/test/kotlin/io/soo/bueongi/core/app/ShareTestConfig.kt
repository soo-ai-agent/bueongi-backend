package io.soo.bueongi.core.app

import io.soo.bueongi.core.share.MutableTestClock
import io.soo.bueongi.core.share.domain.service.ShareService
import io.soo.bueongi.storage.db.share.store.InMemoryShareSessionStore
import io.soo.bueongi.storage.db.share.store.ShareSessionStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

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
