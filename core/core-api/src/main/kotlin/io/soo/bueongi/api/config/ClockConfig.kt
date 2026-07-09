package io.soo.bueongi.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 시간 소스([Clock]) 빈. 위치 공유의 TTL(1~3시간 상한 만료, Redis EXPIRE 계산) 같은 시간 의존 로직이 이 빈을 주입받아
 * 쓰고, 테스트에서는 고정/가변 Clock 으로 교체해 만료 거동을 **결정론적으로** 검증한다(`Instant.now()` 직접 호출 회피).
 */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
