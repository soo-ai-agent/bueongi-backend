package io.soo.bueongi.storage.db.share.store

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import java.net.URI
import java.time.Clock

// RedisAutoConfiguration·DataSourceAutoConfiguration 은 BueongiApplication 에서 제외돼 있다
// — URL 미지정 시 localhost:6379 자동 연결·클래스패스 H2 자동 기동을 막으려는 것. 연결은 여기서 URL 이 있을 때만 만든다.
@Configuration
class ShareStoreConfig {
    // 같은 이름의 빈을 조건부 설정 두 곳에 나눠 두면 IDE 가 조건을 못 읽어 "빈이 둘"로 경고한다 — 메서드 하나에서 고른다.
    // 우선순위: DB > Redis > 인메모리.
    @Bean
    fun locationStore(
        shareSessionRepository: ObjectProvider<ShareSessionRepository>,
        redisTemplate: ObjectProvider<StringRedisTemplate>,
        clock: Clock,
    ): LocationStore {
        val repository: ShareSessionRepository? = shareSessionRepository.ifAvailable
        if (repository != null) {
            return JpaLocationStore(repository, clock)
        }
        val template: StringRedisTemplate = redisTemplate.ifAvailable ?: return InMemoryLocationStore(clock)
        return RedisLocationStore(template, clock)
    }

    @Configuration
    @Conditional(DbConfigured::class)
    @EnableJpaRepositories(
        basePackages = ["io.soo.bueongi.storage.db.share.store"],
        entityManagerFactoryRef = "shareEntityManagerFactory",
        transactionManagerRef = "shareTransactionManager",
    )
    class DbShareStore(
        @param:Value("\${bueongi.share.db-url}") private val dbUrl: String,
    ) {
        @Bean
        fun shareDataSource(): HikariDataSource {
            val config: HikariConfig = HikariConfig()
            config.jdbcUrl = dbUrl
            config.maximumPoolSize = MAX_POOL_SIZE
            return HikariDataSource(config)
        }

        @Bean
        fun shareEntityManagerFactory(shareDataSource: HikariDataSource): LocalContainerEntityManagerFactoryBean {
            val factory: LocalContainerEntityManagerFactoryBean = LocalContainerEntityManagerFactoryBean()
            factory.dataSource = shareDataSource
            factory.setPackagesToScan("io.soo.bueongi.storage.db.share.store")
            factory.jpaVendorAdapter = HibernateJpaVendorAdapter()
            // Flyway 가 없는 저장소라 테이블 하나는 Hibernate 가 만들고 유지한다(update).
            factory.setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "update"))
            return factory
        }

        @Bean
        fun shareTransactionManager(shareEntityManagerFactory: LocalContainerEntityManagerFactoryBean): PlatformTransactionManager {
            return JpaTransactionManager(shareEntityManagerFactory.`object`!!)
        }

        private companion object {
            // 요청량이 작아(5초 폴링·세션 수십 건) 커넥션을 많이 쥘 이유가 없다.
            const val MAX_POOL_SIZE: Int = 5
        }
    }

    @Configuration
    @Conditional(RedisConfigured::class)
    class RedisShareStore(
        @param:Value("\${bueongi.share.redis-url}") private val redisUrl: String,
    ) {
        @Bean(destroyMethod = "destroy")
        fun shareRedisConnectionFactory(): LettuceConnectionFactory {
            val uri: URI = URI(redisUrl)
            var port: Int = DEFAULT_REDIS_PORT
            if (uri.port > 0) {
                port = uri.port
            }
            val config: RedisStandaloneConfiguration = RedisStandaloneConfiguration(uri.host ?: "localhost", port)
            val password: String = uri.userInfo?.substringAfter(':', "").orEmpty()
            if (password.isNotEmpty()) {
                config.setPassword(password)
            }
            return LettuceConnectionFactory(config).apply { afterPropertiesSet() }
        }

        @Bean
        fun shareRedisTemplate(factory: LettuceConnectionFactory): StringRedisTemplate {
            return StringRedisTemplate(factory).apply { afterPropertiesSet() }
        }
    }

    private companion object {
        const val DEFAULT_REDIS_PORT: Int = 6379
    }
}
