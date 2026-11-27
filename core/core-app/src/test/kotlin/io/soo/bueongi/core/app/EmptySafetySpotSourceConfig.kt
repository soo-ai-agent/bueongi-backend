package io.soo.bueongi.core.app

import io.soo.bueongi.storage.db.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSource
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

// 웹 슬라이스 테스트용 — 안심시설이 하나도 없는 저장소(DB 없이 뜬다).
@TestConfiguration
class EmptySafetySpotSourceConfig {
    @Bean
    fun safetySpotSource(): SafetySpotSource {
        return FakeSafetySpotSource()
    }
}
