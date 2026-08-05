package io.soo.bueongi.storage.db.core.apilog

import io.soo.bueongi.core.enums.ApiProvider


/**
 * 외부 API 접속 내역 저장소. 공유 저장소([io.soo.bueongi.storage.db.core.share.LocationStore])와 동일 철학 —
 * Redis 주입 시 [RedisApiCallLogStore], 미주입(로컬/CI/MVP)이면 [InMemoryApiCallLogStore] 가 동일 계약으로 폴백한다.
 *
 * 최근 [API_CALL_LOG_CAP] 건만 유지하는 순환 버퍼다(오래된 것은 축출). 계측은 본 로직의 순수 부수효과이므로,
 * [record] 는 어떤 경우에도 예외를 밖으로 던지지 않는다(계측 실패가 서비스 호출을 깨면 안 된다 — 내부에서 삼키고 로깅만).
 */
interface ApiCallLogStore {
    /** 계측 1건 저장. **절대 예외를 던지지 않는다**(내부 try/catch 로 삼킨다). */
    fun record(log: ApiCallLog)

    /** 최근순(최신 먼저) 조회. [provider] 가 null 이면 전체, 아니면 해당 원천만. 최대 [limit] 건. */
    fun findRecent(provider: String?, limit: Int): List<ApiCallLog>
}

/** 순환 버퍼 상한 — 최근 이만큼만 유지한다(Redis LTRIM / 인메모리 deque 축출). */
const val API_CALL_LOG_CAP: Int = 1000

/** 조회 기본 건수(관리자 API 미지정 시). */
const val API_CALL_LOG_DEFAULT_LIMIT: Int = 100

/** 조회 상한 건수(관리자 API 요청이 이를 넘겨도 이 값으로 절삭). */
const val API_CALL_LOG_MAX_LIMIT: Int = 500

/**
 * 외부 호출 [block] 을 감싸 소요시간·성공여부를 재고 [record] 하는 공통 계측 헬퍼.
 * 성공 판정([success])·비고([detail])는 원천마다 의미가 달라(예: datagokr 는 null=실패, kakao 는 예외=실패)
 * 호출부가 명시한다. 예외가 나면 실패로 기록하고 **그대로 다시 던진다**(기존 폴백/예외처리 흐름 불변).
 *
 * [provider] 는 닫힌 어휘([ApiProvider])로 받고 저장 직전에 [ApiProvider.name] 문자열로 옮긴다 —
 * 원천 이름 오타를 컴파일 시점에 막으면서 저장 포맷(기존 문자열)은 그대로 유지하는 경계다.
 *
 * store 가 null(테스트 슬라이스 등 계측 저장소 미주입)이면 [block] 만 실행한다 — 계측은 순수 부수효과다.
 */
inline fun <T> ApiCallLogStore?.measure(
    provider: ApiProvider,
    endpoint: String,
    success: (T) -> Boolean,
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
