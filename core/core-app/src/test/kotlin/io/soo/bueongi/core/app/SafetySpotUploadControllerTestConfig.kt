package io.soo.bueongi.core.app

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.admin.safetyspot.domain.csv.service.SafetySpotCsvParser
import io.soo.bueongi.core.admin.safetyspot.domain.csv.service.SafetySpotUploadService
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotUploadStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.nio.file.Files

@TestConfiguration
class SafetySpotUploadControllerTestConfig {
    @Bean
    fun safetySpotUploadService(): SafetySpotUploadService {
        val dir: String = Files.createTempDirectory("fac-upload-controller-test").toFile().absolutePath
        return SafetySpotUploadService(
            parser = SafetySpotCsvParser(),
            uploadStore = SafetySpotUploadStore(ObjectMapper(), dir),
            liveStore = FakeLiveStore(),
            refreshStatusStore = SafetySpotRefreshStatusStore(),
        )
    }
}
