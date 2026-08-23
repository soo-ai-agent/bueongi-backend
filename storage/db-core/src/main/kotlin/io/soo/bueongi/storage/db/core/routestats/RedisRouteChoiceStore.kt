package io.soo.bueongi.storage.db.core.routestats

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.storage.db.core.RedisJsonLog
import org.springframework.data.redis.core.StringRedisTemplate

class RedisRouteChoiceStore(
    redis: StringRedisTemplate,
    objectMapper: ObjectMapper = jacksonObjectMapper(),
) : RouteChoiceStore {

    private val entries: RedisJsonLog<RouteChoiceLog> = RedisJsonLog(
        redis = redis,
        objectMapper = objectMapper,
        key = ROUTE_CHOICE_REDIS_KEY,
        cap = ROUTE_CHOICE_CAP,
        type = RouteChoiceLog::class.java,
        label = "경로 선택",
    )

    override fun record(log: RouteChoiceLog) =
        entries.push(log)

    override fun findRecent(limit: Int): List<RouteChoiceLog> {
        val safeLimit: Int = limit.coerceAtLeast(0)
        return entries.readRecent().take(safeLimit)
    }
}
