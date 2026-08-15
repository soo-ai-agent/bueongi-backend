package io.soo.bueongi.storage.db.core.apilog

import io.soo.bueongi.core.enums.ApiProvider

interface ApiCallLogStore {
    /** 계약: 절대 예외를 던지지 않는다(계측 실패가 서비스 호출을 깨면 안 됨 — 내부에서 삼킨다). */
    fun record(log: ApiCallLog)
    fun findRecent(provider: String?, limit: Int): List<ApiCallLog>
}

const val API_CALL_LOG_CAP: Int = 1000

const val API_CALL_LOG_DEFAULT_LIMIT: Int = 100

const val API_CALL_LOG_MAX_LIMIT: Int = 500

inline fun <T> ApiCallLogStore?.measure(
    provider: ApiProvider,
    endpoint: String,
    /** 예외 없이 돌아온 결과도 실패로 볼지 정한다. 기본은 "예외만 실패"(Kakao 검색 0건은 정상 응답). */
    success: (T) -> Boolean = { true },
    detail: (T) -> String? = { null },
    block: () -> T,
): T {
    if (this == null) {
        return block()
    }
    val startedAt: Long = System.currentTimeMillis()
    val result: T = try {
        block()
    } catch (e: Exception) {
        record(
            ApiCallLog(
                atEpochMs = startedAt,
                provider = provider.name,
                endpoint = endpoint,
                success = false,
                durationMs = System.currentTimeMillis() - startedAt,
                detail = "${e.javaClass.simpleName}: ${e.message ?: "메시지 없음"}",
            ),
        )
        throw e
    }
    record(
        ApiCallLog(
            atEpochMs = startedAt,
            provider = provider.name,
            endpoint = endpoint,
            success = success(result),
            durationMs = System.currentTimeMillis() - startedAt,
            detail = detail(result),
        ),
    )
    return result
}
