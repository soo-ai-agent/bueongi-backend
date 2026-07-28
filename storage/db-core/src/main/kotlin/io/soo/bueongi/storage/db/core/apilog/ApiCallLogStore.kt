package io.soo.bueongi.storage.db.core.apilog

/**
 * 외부 API 호출 1건의 계측 기록. 관리자 페이지의 "외부 API 접속 내역"에 그대로 노출된다 —
 * 어떤 원천([provider])의 어떤 종류 호출([endpoint])이 언제([atEpochMs]) 성공/실패했고([success]) 얼마나 걸렸는지([durationMs]).
 * PII 는 담지 않는다(검색어·좌표·토큰 등 사용자 입력은 [detail] 에도 넣지 않는다 — 원인/비고만).
 */
data class ApiCallLog(
    /** 호출 시작 시각(epoch ms). 최근순 정렬·표시용. */
    val atEpochMs: Long,
    /** 원천 식별자: KAKAO | TMAP | DATAGOKR | SAFEMAP. */
    val provider: String,
    /** 호출 종류(원천 내 구분): 예 cctv/bell/safehouse/police/route/search. */
    val endpoint: String,
    val success: Boolean,
    val durationMs: Long,
    /** 실패 사유/비고(성공이면 보통 null). 사용자 입력은 담지 않는다. */
    val detail: String? = null,
)

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
 * store 가 null(테스트 슬라이스 등 계측 저장소 미주입)이면 [block] 만 실행한다 — 계측은 순수 부수효과다.
 */
inline fun <T> ApiCallLogStore?.measure(
    provider: String,
    endpoint: String,
    success: (T) -> Boolean,
    detail: (T) -> String? = { null },
    block: () -> T,
): T {
    if (this == null) {
        return block()
    }
    val startedAt: Long = System.currentTimeMillis()
    val result = try {
        block()
    } catch (e: Exception) {
        record(
            ApiCallLog(
                atEpochMs = startedAt,
                provider = provider,
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
            provider = provider,
            endpoint = endpoint,
            success = success(result),
            durationMs = System.currentTimeMillis() - startedAt,
            detail = detail(result),
        ),
    )
    return result
}
