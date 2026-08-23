package io.soo.bueongi.support.logging

/**
 * 붙잡은 예외 한 건을 로그 한 줄로 옮긴 값. 수신·송신 줄과 칸 순서를 맞춘다.
 *
 * 맨 앞이 방향이다 — `[예외]` 로 시작해 `[수신]`·`[송신]` 과 낱말로 갈린다. 뒤이어
 * 상대(부른 IP 자리에 원천·부품 이름) · 결과 · 대상(경로 또는 하던 일) 순으로 오고,
 * 소요시간 자리부터가 표식 칸이다 — 🔗 짝, ✖ 원인 사슬, ↳ 그래서 무엇을 했는지.
 *
 * 원인은 반드시 사슬로 적는다([causeChainOf]) — 겉 예외만 남기면 진짜 원인이 사라진다.
 */
data class ExceptionLogLine(
    val kind: String,
    val counterpart: String,
    val subject: String,
    val cause: String,
    val result: String = NO_RESPONSE_STATUS,
    val action: String = "",
    /** 어느 호출이었는지 — 페이지·시도 회차·요청 옵션. 적재 계열의 진단은 여기서 시작한다. */
    val paging: String = "",
) {
    fun format(): String {
        val columns: MutableList<String> = mutableListOf(
            padColumn("$EXCEPTION_DIRECTION $kind", DIRECTION_COLUMN_WIDTH),
            padColumn(counterpart, COUNTERPART_COLUMN_WIDTH),
            padColumn(result, STATUS_COLUMN_WIDTH),
            subject,
        )
        val trace: String = traceColumn()
        if (trace.isNotEmpty()) {
            columns.add(trace)
        }
        if (paging.isNotEmpty()) {
            columns.add("📄 $paging")
        }
        columns.add("✖ $cause")
        if (action.isNotEmpty()) {
            columns.add("↳ $action")
        }
        return columns.joinToString(LOG_COLUMN_SEPARATOR)
    }
}

/** 우리가 못 잡아 500·503 으로 나간 예외 — 우리 쪽 결함이다. */
const val UNHANDLED_EXCEPTION: String = "미처리"

/** 붙잡아 폴백·건너뛰기로 넘긴 예외 — 서비스는 계속 돈다. */
const val IGNORED_EXCEPTION: String = "무시"
