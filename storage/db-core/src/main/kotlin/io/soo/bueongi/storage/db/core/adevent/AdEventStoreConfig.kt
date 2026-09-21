package io.soo.bueongi.storage.db.core.adevent

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AdEventStoreConfig {
    @Bean
    fun adEventStore(): AdEventStore = InMemoryAdEventStore()
}
