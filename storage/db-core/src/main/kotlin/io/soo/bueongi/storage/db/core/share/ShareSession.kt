package io.soo.bueongi.storage.db.core.share

import java.time.Instant

/**
 * 공유 1건의 저장 표현. 추측 불가능한 비식별 [token] 과 생성/상한 만료 시각, 그리고 (있다면) 마지막 위치만 담는다.
 * 계정·전화번호 같은 PII 는 저장하지 않는다(안심앱 프라이버시 원칙: 토큰 URL 공유 모델).
 *
 * Redis 모델과 1:1 대응한다 — `share:{token}` HASH(created_at, expires_at) + `location:{token}` HASH(lat, lng, updated_at).
 */
data class ShareSession(
    val token: String,
    /**
     * 위치 쓰기/종료 권한을 가르는 비밀(앱만 보유). 보호자에게 가는 공유 URL/조회 응답에는 절대 포함하지 않는다.
     * 공유 URL의 token(읽기 전용)이 유출돼도 이 비밀이 없으면 위치 조작·임의 종료가 불가하다(읽기/쓰기 분리).
     */
    val ownerSecret: String,
    /** 공유 생성 시각(절대). Redis `share:{token}.created_at`. */
    val createdAt: Instant,
    /** 공유 상한 만료(절대). 이 시각 이후의 조회/갱신은 만료로 처리. Redis 는 native TTL 로, 인메모리는 lazy 비교로 강제. */
    val expiresAt: Instant,
    /** 마지막으로 갱신된 위치. 생성됐으나 아직 위치 보고 전이면 null. */
    val location: StoredLocation? = null,
    /**
     * 길안내 경로 컨텍스트(목적지·경로선·안심시설 마커·총 거리/시간). 앱이 길안내 시작 시 1회 저장하며,
     * 위치와 독립이다(경로만·위치만·둘 다 모두 허용). 저장 전이면 null.
     */
    val route: StoredRoute? = null,
    /**
     * 예정 도착 시각(절대). 생성 시 클라가 ETA 를 제공하면 서버가 산출해 둔다 — 미도착 감시(서버 워치독)의 기준.
     * 없으면(ETA 미제공·구세션) 감시 대상에서 제외한다(하위호환).
     */
    val expectedArrivalAt: Instant? = null,
) {
    /**
     * 위치만 갱신한 새 세션 — 경로 컨텍스트([route])는 보존한다(둘은 독립이라는 불변식을
     * 호출부의 copy 나열이 아니라 엔티티 메서드로 고정).
     */
    fun withLocation(location: StoredLocation): ShareSession = copy(location = location)

    /** 경로 컨텍스트만 갱신한 새 세션 — 기존 위치([location])는 보존한다(위와 동일 불변식). */
    fun withRoute(route: StoredRoute): ShareSession = copy(route = route)

    /** 상한 만료 여부(주입 시각 기준). 저장소(Redis TTL/인메모리 lazy 비교)가 같은 판정을 강제한다. */
    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)
}
