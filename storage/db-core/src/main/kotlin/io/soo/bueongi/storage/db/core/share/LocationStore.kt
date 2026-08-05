package io.soo.bueongi.storage.db.core.share

import java.time.Instant







/**
 * 공유 저장소 추상화. 장소 검색 소스 추상화(상위 도메인의 PlaceProvider)와 같은 패턴으로 Redis 구현([RedisLocationStore])과
 * 인메모리 폴백([InMemoryLocationStore])을 교체 가능하게 한다 — Redis 미주입(로컬/CI/MVP 기본)이면 인메모리가 동작하므로
 * 외부 인프라 없이 전 기능을 동일 계약으로 테스트할 수 있다(장소 SeedPlaceProvider 와 동일 철학).
 *
 * 공유 상한 만료(expiresAt)는 **저장소가 책임진다**([findByToken] 이 만료된 토큰엔 null 반환) —
 * Redis 는 native TTL, 인메모리는 [findByToken] 시점의 lazy 비교로 동치 거동을 보장한다.
 */
interface LocationStore {
    /** health 라벨용 백엔드 식별자. "redis" | "memory"(자격증명·주소는 노출하지 않는 불리언 파생 라벨). */
    val backend: String

    /** 공유를 저장(신규 생성 또는 위치 갱신). location 이 채워졌으면 위치도 함께 동일 TTL 로 저장한다. */
    fun save(session: ShareSession)

    /** token 으로 공유 조회. 없거나 상한 만료가 지났으면 null. */
    fun findByToken(token: String): ShareSession?

    /** 공유를 즉시 종료(삭제). 도착/공유 중단 시 호출 — 이후 [findByToken] 은 null(보호자는 "공유 종료"). 멱등(없는 토큰도 무해). */
    fun deleteByToken(token: String)

    /**
     * 보호자(뷰어)가 위치를 조회했음을 짧은 유효시간 동안 기록한다(시청 중 표시용).
     * 사용자 본인(관리자)이 아닌 GET /location 호출에서만 부른다 — 본인 확인은 카운트하지 않는다.
     */
    fun markViewed(token: String)

    /** 최근 유효시간 내에 보호자 조회가 있었는지(=현재 누군가 보고 있는지). */
    fun isWatched(token: String): Boolean

    /**
     * 현재 활성(상한 미만료) 공유 세션 수 — 관리자 페이지 계측용. 세션 식별 키(`share:{token}`) 기준 1회씩만 센다
     * (위치/경로/시청 키는 제외). Redis 는 native TTL 로 만료 키가 이미 없고, 인메모리는 만료 세션을 제외해 동치 거동을 맞춘다.
     */
    fun countActiveSessions(): Int

    /**
     * 현재 활성(상한 미만료) 공유 세션 전량 — 관리자 목록용. [countActiveSessions] 와 같은 세션 키(`share:{token}`)
     * 기준으로 세션을 복원해 돌려준다(만료면 제외). Redis 는 세션 키를 SCAN 순회하며 [findByToken] 으로 복원하고,
     * 인메모리는 만료 세션을 제외한다(동치 거동). 상세 매핑·정렬·URL 조립은 상위 도메인이 담당한다(저장소는 열거만).
     */
    fun findActiveSessions(): List<ShareSession>
}

/**
 * 보호자 조회 후 이 시간(초) 동안 '시청 중'으로 본다 — 보호자 폴링(5초)의 약 2~3배 여유.
 * 두 구현(Redis TTL / 인메모리 lazy 비교)의 동치 거동은 이 계약의 불변식이므로,
 * 값을 구현마다 두지 않고 계약 파일의 단일 상수로 공유한다(한쪽만 바뀌는 드리프트 방지).
 */
const val VIEWER_WINDOW_SECONDS: Long = 12L
