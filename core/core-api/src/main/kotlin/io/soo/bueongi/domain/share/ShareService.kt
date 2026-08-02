package io.soo.bueongi.domain.share

import io.soo.bueongi.storage.db.core.share.LocationStore
import io.soo.bueongi.storage.db.core.share.ShareSession
import io.soo.bueongi.storage.db.core.share.StoredLocation
import io.soo.bueongi.storage.db.core.share.StoredRoute
import io.soo.bueongi.support.error.ShareSessionNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * 위치 공유 생명주기(생성 / 위치 갱신 / 조회) 오케스트레이션. 저장은 [LocationStore](Redis/인메모리)에 위임하고,
 * 입력은 API 경계에서 검증·변환된 도메인 값([LocationReport]/[StoredRoute]/원시값)만 받는다(웹 DTO 미인지).
 *
 * 안심앱 프라이버시 원칙을 코드로 강제한다:
 * - token 은 추측 불가능한 128-bit URL-safe 랜덤(링크 공유 모델, PII 미저장).
 * - 공유는 1~3시간 상한 TTL 로 자동 만료(무기한 추적 금지) — Redis native TTL 또는 인메모리 lazy 비교.
 * - 모든 시각은 주입된 [clock] 기준이라 만료 거동을 결정론적으로 테스트한다.
 */
@Service
class ShareService(
    private val store: LocationStore,
    private val clock: Clock,
    @Value("\${bueongi.share.base-url}") private val baseUrl: String,
) {
    private val random: SecureRandom = SecureRandom()
    private val tokenEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    /**
     * 공유 생성 → token·share_url·상한 만료 반환. expiresInHours 미지정이면 [DEFAULT_EXPIRES_HOURS].
     * etaSeconds 를 받으면 예정 도착 시각(+[MISSED_ARRIVAL_GRACE_SECONDS])을 잡아 미도착 감시 대상으로 둔다(미제공 시 감시 제외).
     */
    fun create(expiresInHours: Int?, etaSeconds: Int? = null): CreateShareResponse {
        val hours: Int = expiresInHours ?: DEFAULT_EXPIRES_HOURS
        val token: String = newToken()
        val ownerSecret: String = newToken() // 읽기 토큰과 동일 엔트로피(128-bit)의 별도 쓰기 비밀.
        val now: Instant = clock.instant()
        val expiresAt: Instant = now.plusSeconds(hours.toLong() * SECONDS_PER_HOUR)
        val expectedArrivalAt: Instant? = etaSeconds?.let { now.plusSeconds(it.toLong() + MISSED_ARRIVAL_GRACE_SECONDS) }
        store.save(
            ShareSession(
                token = token,
                ownerSecret = ownerSecret,
                createdAt = now,
                expiresAt = expiresAt,
                location = null,
                expectedArrivalAt = expectedArrivalAt,
            ),
        )
        return CreateShareResponse(
            token = token,
            ownerSecret = ownerSecret,
            shareUrl = shareUrlFor(token),
            expiresAt = expiresAt,
        )
    }

    /**
     * 활성(상한 미만료) 공유 세션 목록(관리자 전용). 저장소 열거([LocationStore.findActiveSessions])를
     * [ActiveShareView] 로 매핑한다 — shareUrl 조립·TTL 파생·시청 여부 조회를 도메인에서 수행하고,
     * owner_secret·경로 상세는 노출하지 않는다(프라이버시 불변식). 정렬은 updatedAtMs 내림차순(null 마지막),
     * 동률은 token 오름차순 — 최근 움직인 공유가 위로 오되 위치 미보고 세션은 아래로 모은다.
     */
    fun listActiveSessions(): List<ActiveShareView> {
        val now: Instant = clock.instant()
        return store.findActiveSessions()
            .map { session -> toActiveShareView(session, now) }
            .sortedWith(ACTIVE_SHARE_ORDER)
    }

    /**
     * 활성(상한 미만료) 공유 세션 수(관리자 전용). 컨트롤러가 storage 계층([LocationStore])을 직접 주입받지 않도록
     * 도메인이 개수만 중계한다([storeBackendLabel] 과 같은 이유 — Controller 의존은 domain 까지).
     * 세션 복원 없이 세기만 하므로 [listActiveSessions] 보다 가볍다(현황 배지 전용).
     */
    fun countActiveSessions(): Int = store.countActiveSessions()

    /** 활성 세션 1건 → 관리자 뷰. lat/lng/updatedAtMs 는 위치 미보고 시 null, TTL 은 expiresAt 에서 파생(추가 왕복 없음). */
    private fun toActiveShareView(session: ShareSession, now: Instant): ActiveShareView {
        val location: StoredLocation? = session.location
        val ttlSeconds: Long = Duration.between(now, session.expiresAt).seconds.coerceAtLeast(0)
        return ActiveShareView(
            token = session.token,
            shareUrl = shareUrlFor(session.token),
            lat = location?.lat,
            lng = location?.lng,
            updatedAtMs = location?.updatedAt?.toEpochMilli(),
            ttlSeconds = ttlSeconds,
            viewerActive = store.isWatched(session.token),
        )
    }

    /** 외부 공유용 URL 조립. 생성·목록이 같은 규칙을 공유하도록 한 곳에 둔다(`{base}/share/{token}`). */
    private fun shareUrlFor(token: String): String = "${baseUrl.trimEnd('/')}/share/$token"

    /**
     * 위치 갱신(사용자 단말, 5초마다). 토큰 없음/만료면 [ShareSessionNotFoundException]→404.
     * 경로 컨텍스트는 [ShareSession.withLocation] 이 보존한다(위치와 경로는 독립 — 엔티티 불변식).
     */
    fun updateLocation(token: String, ownerSecret: String?, report: LocationReport): UpdateLocationResponse {
        val session: ShareSession = store.findByToken(token) ?: throw ShareSessionNotFoundException()
        requireOwner(session, ownerSecret) // 쓰기 비밀 검증 — 공유 URL만 아는 사람의 위치 조작 차단.
        val now: Instant = clock.instant()
        val location: StoredLocation = StoredLocation(
            lat = report.lat,
            lng = report.lng,
            updatedAt = now,
            remainingM = report.remainingM,
            remainingS = report.remainingS,
        )
        store.save(session.withLocation(location))
        return UpdateLocationResponse(updatedAt = now, expiresAt = session.expiresAt)
    }

    /**
     * 경로 컨텍스트 저장(앱이 길안내 시작 시 1회). 토큰 없음/만료면 404, 쓰기 비밀 누락/불일치도 404(requireOwner).
     * 기존 위치는 [ShareSession.withRoute] 가 보존한다(위치와 경로는 독립 — 엔티티 불변식).
     */
    fun saveRoute(token: String, ownerSecret: String?, route: StoredRoute): UpdateLocationResponse {
        val session: ShareSession = store.findByToken(token) ?: throw ShareSessionNotFoundException()
        requireOwner(session, ownerSecret)
        val now: Instant = clock.instant()
        store.save(session.withRoute(route))
        return UpdateLocationResponse(updatedAt = now, expiresAt = session.expiresAt)
    }

    /**
     * 경로 컨텍스트 조회(보호자, 인증 없음·1회 fetch). 항상 200 으로 응답하고 상태는 [RouteView] 로 표현한다:
     * 토큰 없음/만료 → `{expired:true, route:null}`, 살아있으나 경로 미저장 → `{expired:false, route:null}`,
     * 저장됨 → `{expired:false, route:{...}}`. 위치 폴링(GET /location)과 달리 시청자로 집계하지 않는다(markViewed 미호출).
     */
    fun getRoute(token: String): RouteView {
        val session: ShareSession = store.findByToken(token)
            ?: return RouteView(expired = true, route = null)
        return RouteView(expired = false, route = session.route?.toRouteContext())
    }

    /**
     * 위치 조회(보호자 웹 5초 폴링). 항상 200 으로 응답하고 상태는 [LocationView] 로 표현한다:
     * 토큰 없음/만료 → `expired:true`(좌표 null), 살아있으나 첫 위치 전 → `expired:false`+좌표 null, 위치 있음 → 좌표.
     */
    fun getLocation(token: String): LocationView {
        val session: ShareSession = store.findByToken(token)
            ?: return LocationView(lat = null, lng = null, updatedAt = null, expired = true)
        // 이 조회는 보호자(뷰어)다 — '시청 중'으로 기록한다. 관리자 열람은 이 경로가 아니라 집계하지 않는
        // [getLocationForAdmin] 을 쓰고, 사용자 본인은 owner_secret 이 필요한 POST /watching 으로만 확인한다.
        store.markViewed(token)
        return locationViewOf(session)
    }

    /**
     * 위치 조회(관리자 앱 내 열람 — 비집계). [getLocation] 과 동일한 [LocationView] 를 주되 [LocationStore.markViewed]
     * 를 호출하지 않는다 — 관리자 열람이 '보호자 시청 중'(viewerActive)으로 잘못 집계되지 않게 한다.
     * 상태 규약은 공개 조회와 완전히 동일하다: 토큰 없음/만료 → `expired:true`(좌표 null),
     * 살아있으나 첫 위치 전 → `expired:false`+좌표 null, 위치 있음 → 좌표.
     */
    fun getLocationForAdmin(token: String): LocationView {
        val session: ShareSession = store.findByToken(token)
            ?: return LocationView(lat = null, lng = null, updatedAt = null, expired = true)
        return locationViewOf(session)
    }

    /**
     * 활성(상한 미만료) 세션 → 위치 뷰. 첫 위치 보고 전이면 `expired:false`+좌표 null, 위치가 있으면 좌표를 싣는다.
     * 순수 조립이라 부수효과가 없다 — 시청 집계([LocationStore.markViewed])는 호출자(보호자 조회만)가 정한다.
     */
    private fun locationViewOf(session: ShareSession): LocationView {
        val overdue: Boolean = isOverdue(session, clock.instant())
        val location: StoredLocation = session.location
            ?: return LocationView(lat = null, lng = null, updatedAt = null, expired = false, overdue = overdue)
        return LocationView(
            lat = location.lat,
            lng = location.lng,
            updatedAt = location.updatedAt,
            expired = false,
            remainingM = location.remainingM,
            remainingS = location.remainingS,
            overdue = overdue,
        )
    }

    /** 예정 도착 시각을 여유([MISSED_ARRIVAL_OVERDUE_SECONDS]) 넘겨 초과했는지 — 보호자 페이지 경고 격상 근거. ETA 미제공이면 false. */
    private fun isOverdue(session: ShareSession, now: Instant): Boolean {
        val expected: Instant = session.expectedArrivalAt ?: return false
        return now.isAfter(expected.plusSeconds(MISSED_ARRIVAL_OVERDUE_SECONDS))
    }

    /**
     * 운영 헬스 라벨용 저장소 백엔드 식별자("redis"|"memory"). 컨트롤러(HealthController)가 storage 계층
     * ([LocationStore])을 직접 주입받지 않도록 도메인이 라벨만 중계한다(Controller 의존은 domain 까지 —
     * 레이어 규칙). 저장소 종류에서 파생한 라벨일 뿐, Redis URL·자격증명은 절대 노출하지 않는다.
     */
    fun storeBackendLabel(): String = store.backend

    /**
     * 공유 종료(사용자 도착/중단). 쓰기 비밀이 일치할 때만 저장소에서 삭제 → 이후 조회는 만료(보호자 "공유 종료").
     * 없는/이미 만료된 토큰은 멱등 no-op(200). 비밀 불일치는 종료하지 않고 404 — 임의 종료(가용성 공격) 차단.
     */
    fun end(token: String, ownerSecret: String?) {
        val session: ShareSession = store.findByToken(token) ?: return // 이미 없음/만료 → 멱등.
        requireOwner(session, ownerSecret)
        store.deleteByToken(token)
    }

    /**
     * '보호자가 보고 있는지' + 현재 공유 위치 조회(사용자 본인 = 관리자 전용). owner_secret 이 맞아야 하고,
     * 이 호출 자체는 시청자로 기록하지 않는다 — 관리자(본인)의 상태 확인/미리보기가 '시청 중'으로 잘못 잡히지 않게 한다.
     * 위치를 함께 돌려주므로 관리자가 보호자에게 보이는 화면을 '따로' 미리볼 수 있다(시청자 미집계). 없는/만료 토큰은 expired=true.
     */
    fun getWatchingStatus(token: String, ownerSecret: String?): WatchResponse {
        val session: ShareSession = store.findByToken(token)
            ?: return WatchResponse(watching = false, expired = true)
        requireOwner(session, ownerSecret)
        val location: StoredLocation? = session.location
        return WatchResponse(
            watching = store.isWatched(token),
            lat = location?.lat,
            lng = location?.lng,
            updatedAt = location?.updatedAt,
            expired = false,
            remainingM = location?.remainingM,
            remainingS = location?.remainingS,
        )
    }

    /**
     * 쓰기 권한 비밀 검증. 상수 시간 비교([MessageDigest.isEqual])로 타이밍 누출을 피한다.
     * 누락/불일치는 [ShareSessionNotFoundException](404)로 처리 — 공유 URL(읽기 토큰)만 아는 사람의
     * 위치 조작·임의 종료를 막고, 토큰 존재 여부도 드러내지 않는다(불투명 응답).
     */
    private fun requireOwner(session: ShareSession, providedSecret: String?) {
        val provided: String = providedSecret ?: throw ShareSessionNotFoundException()
        val matches: Boolean = MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8),
            session.ownerSecret.toByteArray(Charsets.UTF_8),
        )
        if (!matches) {
            throw ShareSessionNotFoundException()
        }
    }

    /** 추측 불가능한 128-bit URL-safe 랜덤 토큰(22자, 패딩 없음). */
    private fun newToken(): String {
        val bytes: ByteArray = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return tokenEncoder.encodeToString(bytes)
    }

    private companion object {
        /** token 엔트로피(바이트). 16바이트 = 128-bit. */
        const val TOKEN_BYTES: Int = 16
        const val SECONDS_PER_HOUR: Long = 3600L

        /**
         * 관리자 목록 정렬 — updatedAtMs 내림차순(위치 있는 세션 먼저, 최근 갱신 순), 동률·위치 미보고(null) 그룹은 token 오름차순.
         * 첫 키가 null/비null 을 먼저 가르므로 두 번째 키의 `?: 0L` 은 null 그룹 내부에서만 쓰여(전원 동률→token) 안전하다.
         */
        val ACTIVE_SHARE_ORDER: Comparator<ActiveShareView> =
            compareByDescending<ActiveShareView> { it.updatedAtMs != null }
                .thenByDescending { it.updatedAtMs ?: 0L }
                .thenBy { it.token }
    }
}

/** 위치 보고(도메인 입력) — API 경계 검증을 통과한 비null 좌표 + 선택 잔여 거리/시간. */
data class LocationReport(
    val lat: Double,
    val lng: Double,
    val remainingM: Double? = null,
    val remainingS: Double? = null,
)
