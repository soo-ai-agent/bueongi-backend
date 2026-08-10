package io.soo.bueongi.storage.db.core.apilog

import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
