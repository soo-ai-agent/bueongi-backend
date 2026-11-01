package io.soo.bueongi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.runApplication

/**
 * 공식 문서 「Disabling Specific Auto-configuration Classes」의 방식이다.
 * - https://docs.spring.io/spring-boot/reference/using/using-the-springbootapplication-annotation.html
 *
 * 위치 공유 저장소 연결(DB·Redis)은 ShareStoreConfig 가 URL 이 설정됐을 때만 직접 만들므로,
 * 자동 설정을 켜 두면 URL 없이도 localhost Redis 연결·클래스패스 H2 자동 기동을 해서 끈다.
 * - https://docs.spring.io/spring-boot/reference/using/auto-configuration.html
 */
@SpringBootApplication(
    exclude = [
        RedisAutoConfiguration::class,
        RedisRepositoriesAutoConfiguration::class,
        DataSourceAutoConfiguration::class,
        DataSourceTransactionManagerAutoConfiguration::class,
        JdbcTemplateAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        JpaRepositoriesAutoConfiguration::class,
    ],
)
class BueongiApplication

fun main(args: Array<String>) {
    runApplication<BueongiApplication>(*args)
}
