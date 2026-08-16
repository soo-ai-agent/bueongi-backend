package io.soo.bueongi.storage.db.core

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * 담기는 것이 전부 관측용이라 저장이 실패해도 서비스는 돌아야 한다.
 * 그래서 예외를 삼키고 기록만 건너뛴다 — 세 저장소가 이 규칙을 어기지 않게 한곳에 둔다.
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

    /** 읽기 실패도 빈 목록 — 화면에 0건으로 보일 뿐 장애가 아니다. */
    fun readRecent(): List<T> {
        val raw: List<String> = try {
            redis.opsForList().range(key, 0, -1).orEmpty()
        } catch (e: Exception) {
            log.warn("{} 조회 실패: {}", label, e.message)
            return emptyList()
        }
        val parsed: ArrayList<T> = ArrayList()
        for (json in raw) {
            try {
                parsed.add(objectMapper.readValue(json, type))
            } catch (e: Exception) {
                log.warn("{} 역직렬화 실패(건너뜀): {}", label, e.message)
            }
        }
        return parsed
    }
}
