package io.soo.bueongi.storage.db.core

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate

// 담기는 것이 전부 관측용이라, 읽기·쓰기 실패를 삼키고 빈 결과로 답한다.
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

    fun readRecent(): List<T> {
        val raw: List<String> =
            try {
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
