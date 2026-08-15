package io.soo.bueongi.storage.db.core.routestats

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.storage.db.core.RedisJsonLog
import org.springframework.data.redis.core.StringRedisTemplate

class RedisRouteStatsStore(
    redis: StringRedisTemplate,
    objectMapper: ObjectMapper = jacksonObjectMapper(),
) : RouteStatsStore {

    private val entries: RedisJsonLog<RouteRecommendationLog> = RedisJsonLog(
        redis = redis,
        objectMapper = objectMapper,
        key = "routestats",
        cap = ROUTE_STATS_CAP,
        type = RouteRecommendationLog::class.java,
        label = "추천 통계",
    )

    override fun record(log: RouteRecommendationLog) = entries.push(log)

    override fun findRecent(limit: Int): List<RouteRecommendationLog> =
        entries.readRecent().take(limit.coerceAtLeast(0))
}
