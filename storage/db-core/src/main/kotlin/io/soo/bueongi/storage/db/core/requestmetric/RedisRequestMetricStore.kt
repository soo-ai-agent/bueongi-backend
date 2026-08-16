package io.soo.bueongi.storage.db.core.requestmetric

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.storage.db.core.RedisJsonLog
import org.springframework.data.redis.core.StringRedisTemplate

class RedisRequestMetricStore(
    redis: StringRedisTemplate,
    objectMapper: ObjectMapper = jacksonObjectMapper(),
) : RequestMetricStore {

    private val entries: RedisJsonLog<RequestMetric> = RedisJsonLog(
        redis = redis,
        objectMapper = objectMapper,
        key = "requestmetric",
        cap = REQUEST_METRIC_CAP,
        type = RequestMetric::class.java,
        label = "수신 요청 메트릭",
    )

    override fun record(metric: RequestMetric) =
        entries.push(metric)

    override fun findRecent(limit: Int): List<RequestMetric> {
        val safeLimit: Int = limit.coerceAtLeast(0)
        return entries.readRecent().take(safeLimit)
    }
}
