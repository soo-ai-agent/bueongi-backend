package io.soo.bueongi.storage.db.core.apilog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate

class RedisApiCallLogStore(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : ApiCallLogStore {

    private val logger: Logger = LoggerFactory.getLogger(RedisApiCallLogStore::class.java)

    override fun record(log: ApiCallLog) {
        try {
            redis.opsForList().leftPush(KEY, objectMapper.writeValueAsString(log))
            redis.opsForList().trim(KEY, 0, (API_CALL_LOG_CAP - 1).toLong())
        } catch (e: Exception) {
            logger.warn("API 호출 로그 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(provider: String?, limit: Int): List<ApiCallLog> {
        val raw: List<String> = try {
            redis.opsForList().range(KEY, 0, -1).orEmpty()
        } catch (e: Exception) {
            logger.warn("API 호출 로그 조회 실패: {}", e.message)
            return emptyList()
        }
        return raw.asSequence()
            .mapNotNull { parseOrNull(it) }
            .filter { provider == null || it.provider == provider }
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    private fun parseOrNull(json: String): ApiCallLog? =
        try {
            objectMapper.readValue<ApiCallLog>(json)
        } catch (e: Exception) {
            logger.warn("API 호출 로그 역직렬화 실패(건너뜀): {}", e.message)
            null
        }

    private companion object {
        const val KEY: String = "apilog"
    }
}
