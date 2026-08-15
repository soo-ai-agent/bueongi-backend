package io.soo.bueongi.storage.db.core.apilog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.storage.db.core.RedisJsonLog
import org.springframework.data.redis.core.StringRedisTemplate

class RedisApiCallLogStore(
    redis: StringRedisTemplate,
    objectMapper: ObjectMapper = jacksonObjectMapper(),
) : ApiCallLogStore {

    private val entries: RedisJsonLog<ApiCallLog> = RedisJsonLog(
        redis = redis,
        objectMapper = objectMapper,
        key = "apilog",
        cap = API_CALL_LOG_CAP,
        type = ApiCallLog::class.java,
        label = "API 호출 로그",
    )

    override fun record(log: ApiCallLog) =
        entries.push(log)

    override fun findRecent(provider: String?, limit: Int): List<ApiCallLog> =
        entries.readRecent()
            .asSequence()
            .filter { provider == null || it.provider == provider }
            .take(limit.coerceAtLeast(0))
            .toList()
}
