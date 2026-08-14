package io.soo.bueongi.storage.db.core.requestmetric

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate

class RedisRequestMetricStore(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : RequestMetricStore {

    private val logger: Logger = LoggerFactory.getLogger(RedisRequestMetricStore::class.java)

    override fun record(metric: RequestMetric) {
        try {
            redis.opsForList().leftPush(KEY, objectMapper.writeValueAsString(metric))
            redis.opsForList().trim(KEY, 0, (REQUEST_METRIC_CAP - 1).toLong())
        } catch (e: Exception) {
            logger.warn("수신 요청 메트릭 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(limit: Int): List<RequestMetric> {
        val raw: List<String> = try {
            redis.opsForList().range(KEY, 0, -1).orEmpty()
        } catch (e: Exception) {
            logger.warn("수신 요청 메트릭 조회 실패: {}", e.message)
            return emptyList()
        }
        return raw.asSequence()
            .mapNotNull { parseOrNull(it) }
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    private fun parseOrNull(json: String): RequestMetric? =
        try {
            objectMapper.readValue<RequestMetric>(json)
        } catch (e: JacksonException) {
            logger.warn("수신 요청 메트릭 역직렬화 실패(건너뜀): {}", e.message)
            null
        }

    private companion object {
        const val KEY: String = "requestmetric"
    }
}
