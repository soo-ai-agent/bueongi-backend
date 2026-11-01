package io.soo.bueongi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.runApplication

/**
 * 공식 문서 「Disabling Specific Auto-configuration Classes」의 방식이다.
 * - https://docs.spring.io/spring-boot/reference/using/using-the-springbootapplication-annotation.html
 *
 * Redis 연결은 ShareStoreConfig 가 URL 이 설정됐을 때만 직접 만들므로, 자동 설정을 켜 두면 URL 없이도 localhost Redis 에 연결하려 해서 끈다.
 * - https://docs.spring.io/spring-boot/reference/using/auto-configuration.html
 */
@SpringBootApplication(exclude = [RedisAutoConfiguration::class, RedisRepositoriesAutoConfiguration::class])
class BueongiApplication

fun main(args: Array<String>) {
    runApplication<BueongiApplication>(*args)
}
