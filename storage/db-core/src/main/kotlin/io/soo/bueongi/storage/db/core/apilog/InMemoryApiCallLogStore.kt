package io.soo.bueongi.storage.db.core.apilog

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Redis 미주입(로컬/CI/MVP 기본) 시 동작하는 인메모리 접속 내역 저장소. 공유 저장소의 인메모리 폴백과 동일 철학 —
 * 외부 인프라 없이 동일 [ApiCallLogStore] 계약으로 동작·테스트된다. 단일 인스턴스 가정(재시작 시 휘발).
 *
 * 최신이 앞([addFirst])인 capped deque — 상한([API_CALL_LOG_CAP]) 초과 시 가장 오래된 것을 뒤에서 축출한다.
 */
class InMemoryApiCallLogStore : ApiCallLogStore {

    private val logger: Logger = LoggerFactory.getLogger(InMemoryApiCallLogStore::class.java)
    private val logs: ArrayDeque<ApiCallLog> = ArrayDeque<ApiCallLog>()
    private val lock: Any = Any()

    override fun record(log: ApiCallLog) {
        try {
            synchronized(lock) {
                logs.addFirst(log)
                while (logs.size > API_CALL_LOG_CAP) {
                    logs.removeLast()
                }
            }
        } catch (e: Exception) {
            logger.warn("API 호출 로그 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(provider: String?, limit: Int): List<ApiCallLog> {
        val snapshot: List<ApiCallLog> = synchronized(lock) { logs.toList() }
        return snapshot.asSequence()
            .filter { provider == null || it.provider == provider }
            .take(limit.coerceAtLeast(0))
            .toList()
    }
}
