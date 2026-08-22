package io.soo.bueongi.storage.db.core.externalcall

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.storage.db.core.RedisJsonLog
import org.springframework.data.redis.core.StringRedisTemplate

class RedisExternalCallMetricStore(
    redis: StringRedisTemplate,
    objectMapper: ObjectMapper = jacksonObjectMapper(),
) : ExternalCallMetricStore {

    private val entries: RedisJsonLog<ExternalCallMetric> = RedisJsonLog(
        redis = redis,
        objectMapper = objectMapper,
        key = "externalcallmetric",
        cap = EXTERNAL_CALL_METRIC_CAP,
        type = ExternalCallMetric::class.java,
        label = "외부 API 호출 기록",
    )

    override fun record(metric: ExternalCallMetric) =
        entries.push(metric)

    override fun findRecent(limit: Int): List<ExternalCallMetric> {
        val safeLimit: Int = limit.coerceAtLeast(0)
        return entries.readRecent().take(safeLimit)
    }
}
