package io.soo.bueongi.storage.db.core

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Redis 리스트에 JSON 을 쌓되 최근 [cap] 건만 남기는 기록장. 새 항목이 앞에 온다.
 *
 * 여기 담기는 것(호출 로그·요청 지표·추천 통계)은 전부 **관측용**이라, 저장이 실패해도
 * 서비스는 계속 돌아야 한다. 그래서 모든 예외를 [label] 과 함께 경고 로그로 삼키고
 * 기록은 조용히 건너뛴다 — 이 규칙이 세 저장소에서 어긋나지 않도록 한곳에 둔다.
 */
class RedisJsonLog<T : Any>(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val key: String,
    private val cap: Int,
    private val type: Class<T>,
    private val label: String,
) {

    private val log: Logger = LoggerFactory.getLogger(RedisJsonLog::class.java)

    fun push(item: T) {
        try {
            redis.opsForList().leftPush(key, objectMapper.writeValueAsString(item))
            redis.opsForList().trim(key, 0, (cap - 1).toLong())
        } catch (e: Exception) {
            log.warn("{} 기록 실패(무시): {}", label, e.message)
        }
    }

    /** 최근 순으로 전부. 읽기 실패도 빈 목록으로 돌린다(화면에 0건으로 보일 뿐 장애가 아니다). */
    fun readRecent(): List<T> {
        val raw: List<String> = try {
            redis.opsForList().range(key, 0, -1).orEmpty()
        } catch (e: Exception) {
            log.warn("{} 조회 실패: {}", label, e.message)
            return emptyList()
        }
        return raw.mapNotNull { parseOrNull(it) }
    }

    private fun parseOrNull(json: String): T? =
        try {
            objectMapper.readValue(json, type)
        } catch (e: Exception) {
            log.warn("{} 역직렬화 실패(건너뜀): {}", label, e.message)
            null
        }
}
