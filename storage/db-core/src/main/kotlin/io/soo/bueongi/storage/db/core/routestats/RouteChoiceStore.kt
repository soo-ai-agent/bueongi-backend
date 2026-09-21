package io.soo.bueongi.storage.db.core.routestats

// 추천 창과 갈라 둔다 — 선택 한 건 뒤에 추천이 여러 건 쌓여 한 창이면 선택이 밀려난다.
interface RouteChoiceStore {
    // 계약: 절대 예외를 던지지 않는다 — 기록 실패가 사용자 흐름을 깨면 안 된다.
    fun record(log: RouteChoiceLog)

    fun findRecent(limit: Int): List<RouteChoiceLog>
}

const val ROUTE_CHOICE_CAP: Int = 2000

// 추천 창(`routestats`)과 달라야 하는 Redis 키 — 같으면 두 기록이 서로를 밀어낸다.
const val ROUTE_CHOICE_REDIS_KEY: String = "routechoices"
