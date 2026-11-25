package io.soo.bueongi.core.app

import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.util.Properties

@TestConfiguration
class AppInfoControllerTestConfig {
    @Bean
    fun buildProperties(): BuildProperties {
        val entries = Properties()
        entries["version"] = "1.2.3"
        return BuildProperties(entries)
    }
}
