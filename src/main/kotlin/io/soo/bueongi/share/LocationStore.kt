package io.soo.bueongi.share

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
)

/**
 * 저장된 위치 1건. 좌표 + 갱신시각에 더해, 앱이 길안내 중 보고한 목적지까지의 잔여 거리/시간(선택)을 담는다.
 * Redis `location:{token}` HASH 와 1:1 대응하며, 공유 상한 만료(expiresAt)와 동일 TTL 로 함께 사라진다.
 */
data class StoredLocation(
    val lat: Double,
    val lng: Double,
    val updatedAt: Instant,
    /** 목적지까지 남은 거리(미터). 앱이 보고했을 때만 채워지고, 아니면 null. */
    val remainingM: Double? = null,
    /** 목적지까지 남은 예상 시간(초). 앱이 보고했을 때만 채워지고, 아니면 null. */
    val remainingS: Double? = null,
)

/**
 * 저장된 길안내 경로 컨텍스트. 보호자 페이지가 앱과 동일한 지도를 재구성하는 데 필요한 최소 정보다 —
 * 목적지·경로선 좌표열·경로변 안심시설 마커·총 거리/시간. 공유 상한 만료(expiresAt)와 동일 TTL 로 함께 사라진다.
 * (웹 DTO 와 분리된 도메인 표현 — [StoredLocation] 과 동일 철학.)
 */
data class StoredRoute(
    val destination: StoredRouteDestination,
    val path: List<StoredCoord>,
    val markers: List<StoredMarker>,
    val totalDistanceM: Int?,
    val totalTimeS: Int?,
)

/** 경로 목적지(좌표 + 표시명). */
data class StoredRouteDestination(
    val name: String?,
    val lat: Double,
    val lng: Double,
)

/** 경로선 좌표 1점. */
data class StoredCoord(
    val lat: Double,
    val lng: Double,
)

/** 경로변 안심시설 마커 1건(좌표 + 표시 힌트 + 정보 카드용 부가 필드). */
data class StoredMarker(
    val type: String,
    val lat: Double,
    val lng: Double,
    val x: Double?,
    val y: Double?,
    val name: String?,
    val purpose: String?,
    val cameraCount: Int?,
    val address: String?,
    val phone: String?,
)

/**
 * 공유 저장소 추상화. 장소 검색의 [io.soo.bueongi.places.PlaceProvider] 패턴을 차용해 Redis 구현([RedisLocationStore])과
 * 인메모리 폴백([InMemoryLocationStore])을 교체 가능하게 한다 — Redis 미주입(로컬/CI/MVP 기본)이면 인메모리가 동작하므로
 * 외부 인프라 없이 전 기능을 동일 계약으로 테스트할 수 있다(장소 SeedPlaceProvider 와 동일 철학).
 *
 * 공유 상한 만료(expiresAt)는 **저장소가 책임진다**([find] 가 만료된 토큰엔 null 반환) —
 * Redis 는 native TTL, 인메모리는 [find] 시점의 lazy 비교로 동치 거동을 보장한다.
 */
interface LocationStore {
    /** health 라벨용 백엔드 식별자. "redis" | "memory"(자격증명·주소는 노출하지 않는 불리언 파생 라벨). */
    val backend: String

    /** 공유를 저장(신규 생성 또는 위치 갱신). location 이 채워졌으면 위치도 함께 동일 TTL 로 저장한다. */
    fun save(session: ShareSession)

    /** token 으로 공유 조회. 없거나 상한 만료가 지났으면 null. */
    fun find(token: String): ShareSession?

    /** 공유를 즉시 종료(삭제). 도착/공유 중단 시 호출 — 이후 [find] 는 null(보호자는 "공유 종료"). 멱등(없는 토큰도 무해). */
    fun delete(token: String)

    /**
     * 보호자(뷰어)가 위치를 조회했음을 짧은 유효시간 동안 기록한다(시청 중 표시용).
     * 사용자 본인(관리자)이 아닌 GET /location 호출에서만 부른다 — 본인 확인은 카운트하지 않는다.
     */
    fun markViewed(token: String)

    /** 최근 유효시간 내에 보호자 조회가 있었는지(=현재 누군가 보고 있는지). */
    fun isWatched(token: String): Boolean
}
