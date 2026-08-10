package io.soo.bueongi.storage.db.core.routestats

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate

class RedisRouteStatsStore(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : RouteStatsStore {

    private val logger: Logger = LoggerFactory.getLogger(RedisRouteStatsStore::class.java)

    override fun record(log: RouteRecommendationLog) {
        try {
            redis.opsForList().leftPush(KEY, objectMapper.writeValueAsString(log))
            redis.opsForList().trim(KEY, 0, (ROUTE_STATS_CAP - 1).toLong())
        } catch (e: Exception) {
            logger.warn("추천 통계 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(limit: Int): List<RouteRecommendationLog> {
        val raw: List<String> = try {
            redis.opsForList().range(KEY, 0, -1).orEmpty()
        } catch (e: Exception) {
            logger.warn("추천 통계 조회 실패: {}", e.message)
            return emptyList()
        }
        return raw.asSequence()
            .mapNotNull { parseOrNull(it) }
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    private fun parseOrNull(json: String): RouteRecommendationLog? =
        try {
            objectMapper.readValue<RouteRecommendationLog>(json)
        } catch (e: Exception) {
            logger.warn("추천 통계 역직렬화 실패(건너뜀): {}", e.message)
            null
        }

    private companion object {
        const val KEY: String = "routestats"
    }
}
