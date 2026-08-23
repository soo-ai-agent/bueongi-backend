package io.soo.bueongi.storage.db.core.routestats

/**
 * 고른 경로를 담는 창. 추천 창([RouteStatsStore])과 갈라 둔다 —
 * 한 번의 선택 뒤에는 추천이 여러 건 쌓이므로, 한 창을 같이 쓰면 추천이 선택을 밀어내
 * "최근 N건"에서 선택이 사라진다(관리자·사용자 요청 지표를 가른 것과 같은 이유).
 */
interface RouteChoiceStore {
    /** 계약: 절대 예외를 던지지 않는다(기록 실패가 사용자 흐름을 깨면 안 됨 — 내부에서 삼키고 로그만 남긴다). */
    fun record(log: RouteChoiceLog)

    fun findRecent(limit: Int): List<RouteChoiceLog>
}

const val ROUTE_CHOICE_CAP: Int = 2000

/** 추천 창(`routestats`)과 다른 키여야 두 기록이 서로를 밀어내지 않는다. */
const val ROUTE_CHOICE_REDIS_KEY: String = "routechoices"
