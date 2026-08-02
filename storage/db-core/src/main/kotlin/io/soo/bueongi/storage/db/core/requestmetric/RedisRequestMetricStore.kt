package io.soo.bueongi.storage.db.core.requestmetric

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Redis 기반 수신 요청 메트릭 저장소. 공유 저장소([io.soo.bueongi.storage.db.core.share.RedisLocationStore])와 같은
 * `StringRedisTemplate` 을 재사용하며, `bueongi.share.redis-url` 주입 시에만 [RequestMetricStoreConfig] 가 이 빈을 등록한다.
 *
 * 키 모델: 단일 리스트 `requestmetric` — [record] 는 `LPUSH`(최신을 머리에) 후 `LTRIM 0..CAP-1` 로 최근 [REQUEST_METRIC_CAP] 건만 유지.
 * [findRecent] 는 `LRANGE 0 -1`(머리=최신부터) 후 limit 절삭. 값은 공유 경로와 동일하게 JSON 문자열로 직렬화한다.
 */
class RedisRequestMetricStore(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : RequestMetricStore {

    private val logger: Logger = LoggerFactory.getLogger(RedisRequestMetricStore::class.java)

    override fun record(metric: RequestMetric) {
        // 계측 실패가 실제 요청을 깨면 안 된다 — 삼키고 경고만(계약: record 는 예외를 밖으로 던지지 않는다).
        try {
            redis.opsForList().leftPush(KEY, objectMapper.writeValueAsString(metric))
            redis.opsForList().trim(KEY, 0, (REQUEST_METRIC_CAP - 1).toLong())
        } catch (e: Exception) {
            logger.warn("수신 요청 메트릭 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(limit: Int): List<RequestMetric> {
        val raw: List<String> = try {
            redis.opsForList().range(KEY, 0, -1).orEmpty()
        } catch (e: Exception) {
            logger.warn("수신 요청 메트릭 조회 실패: {}", e.message)
            return emptyList()
        }
        return raw.asSequence()
            .mapNotNull { parseOrNull(it) }
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    /** 손상 JSON 은 건너뛴다(모델 변경/데이터 손상이 메트릭 조회 전체를 깨지 않게). */
    private fun parseOrNull(json: String): RequestMetric? =
        try {
            objectMapper.readValue<RequestMetric>(json)
        } catch (e: Exception) {
            logger.warn("수신 요청 메트릭 역직렬화 실패(건너뜀): {}", e.message)
            null
        }

    private companion object {
        const val KEY: String = "requestmetric"
    }
}
