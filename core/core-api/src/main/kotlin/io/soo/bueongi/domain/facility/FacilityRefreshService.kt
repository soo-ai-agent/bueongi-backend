package io.soo.bueongi.domain.facility

import io.soo.bueongi.client.datagokr.DataGoKrFacilityClient
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.safemap.SafemapPoliceClient
import io.soo.bueongi.client.safemap.SafemapPoliceFacility
import io.soo.bueongi.client.safemap.SafetydataStreetlight
import io.soo.bueongi.client.safemap.SafetydataStreetlightClient
import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import io.soo.bueongi.storage.db.core.apilog.measure
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class FacilityRefreshService(
    private val dataGoKrClient: DataGoKrFacilityClient,
    private val safemapClient: SafemapPoliceClient,
    private val streetlightClient: SafetydataStreetlightClient,
    private val liveStore: FacilityLiveStore,
    private val alertNotifier: AlertNotifier? = null,
    private val apiCallLogStore: ApiCallLogStore? = null,
    private val refreshStatusStore: FacilityRefreshStatusStore? = null,
) : LiveFacilitySources {
    private val log: Logger = LoggerFactory.getLogger(FacilityRefreshService::class.java)

    /** 기동 갱신(taskExecutor)과 주기 갱신(스케줄러)이 다른 스레드라 겹칠 수 있어 진입을 하나로 막는다. */
    private val refreshInProgress: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var lastRefreshStartedAtMs: Long = 0L

    val isEnabled: Boolean
        get() = dataGoKrClient.isEnabled || safemapClient.isEnabled || streetlightClient.isEnabled

    /**
     * 라이브 갱신 대상 시설 종류 — 원천 클라이언트가 켜진 것만.
     * 여기 없는 종류는 파일 적재분만 쓰므로 "갱신 이력 없음"이 정상이다(관리자 화면이 skipped 로 구분한다).
     *
     * **data.go.kr 전량 덤프 2종(CCTV·보안등)은 라이브 갱신에서 제외한다.**
     * 두 엔드포인트 모두 지역·좌표 필터를 지원하지 않고(어떤 파라미터를 줘도 totalCount 불변),
     * 페이지당 100건 상한이라 1회 갱신 호출 수가 일일 쿼터(1,000)를 크게 넘는다:
     * CCTV 37.7만건=3,773회, 보안등 184만건=18,395회. 매번 쿼터를 소진하고 503(SERVICETIMEOUT)을 부른다.
     * 남기는 2종만으로 BELL 887 + SAFEHOUSE 29 = 916회라 쿼터 안에 들어온다.
     *
     * 두 종류는 파이프라인 CSV/CDN 적재분을 쓰고, 앱의 위치 조회는 어차피 로컬 공간색인(GridSpatialIndex)이 한다.
     * 가로등은 safetydata 원천(페이지당 1,000건·별도 쿼터)이 계속 담당한다.
     * 쿼터가 상향되면 클라이언트 메서드(fetchCctv·fetchSecurityLamps)가 그대로 있으니 다시 연결하면 된다.
     */
    override fun liveRefreshableTypes(): Set<FacilityType> {
        val types: LinkedHashSet<FacilityType> = LinkedHashSet<FacilityType>()
        if (dataGoKrClient.isEnabled) {
            types.add(FacilityType.BELL)
            types.add(FacilityType.SAFEHOUSE)
        }
        if (safemapClient.isEnabled) {
            types.add(FacilityType.POLICE)
        }
        if (streetlightClient.isEnabled) {
            types.add(FacilityType.LAMP)
        }
        return types
    }

    /**
     * 라이브 갱신을 1회 수행한다. 갱신 1회가 수백~수천 회 외부 호출이라 반복 진입을 두 겹으로 막는다:
     * 진행 중이면 즉시 건너뛰고(동시 실행 방지), 직전 시작으로부터 최소 간격이 지나지 않았어도 건너뛴다.
     * 건너뛴 경우 빈 결과를 돌려주고 기존 데이터를 그대로 유지한다.
     */
    fun refreshAll(): Map<FacilityType, Int?> {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.info("안심시설 라이브 갱신이 이미 진행 중 — 이번 요청은 건너뛴다(중복 호출 방지)")
            return emptyMap()
        }
        try {
            return refreshWhenDue()
        } finally {
            refreshInProgress.set(false)
        }
    }

    private fun refreshWhenDue(): Map<FacilityType, Int?> {
        val now: Long = System.currentTimeMillis()
        val sinceLastMs: Long = now - lastRefreshStartedAtMs
        if (lastRefreshStartedAtMs > 0L && sinceLastMs < FacilityRefreshPolicy.MIN_INTERVAL_MS) {
            log.info(
                "안심시설 라이브 갱신 최소 간격 미도달({}분 전 실행) — 이번 요청은 건너뛴다(반복 호출 방지)",
                sinceLastMs / 60_000L,
            )
            return emptyMap()
        }
        lastRefreshStartedAtMs = now
        return collectAll()
    }

    private fun collectAll(): Map<FacilityType, Int?> {
        val liveTypes: Set<FacilityType> = liveRefreshableTypes()
        val result: LinkedHashMap<FacilityType, Int?> = LinkedHashMap<FacilityType, Int?>()
        if (FacilityType.BELL in liveTypes) {
            result[FacilityType.BELL] = refresh(FacilityType.BELL) {
                measureFetch(ApiProvider.DATAGOKR, "bell") { dataGoKrClient.fetchEmergencyBells() }
                    ?.mapNotNull { it.toFacilityPoint(FacilityType.BELL) }
            }
            result[FacilityType.SAFEHOUSE] = refresh(FacilityType.SAFEHOUSE) {
                measureFetch(ApiProvider.DATAGOKR, "safehouse") { dataGoKrClient.fetchSafehouses() }
                    ?.mapNotNull { it.toFacilityPoint(FacilityType.SAFEHOUSE) }
            }
        } else {
            log.info("data.go.kr 키 미설정 — CCTV·비상벨·안심지킴이집 라이브 갱신 건너뜀(파일 적재분 유지)")
        }
        if (FacilityType.POLICE in liveTypes) {
            result[FacilityType.POLICE] = refresh(FacilityType.POLICE) {
                measureFetch(ApiProvider.SAFEMAP, "police") { safemapClient.fetchPoliceFacilities() }
                    ?.map { it.toFacilityPoint() }
            }
        } else {
            log.info("safemap 키 미설정 — 치안시설(파출소) 라이브 갱신 건너뜀(파일 적재분 유지)")
        }
        if (FacilityType.LAMP in liveTypes) {
            result[FacilityType.LAMP] = refresh(FacilityType.LAMP) { collectLampPoints() }
        } else {
            log.info("safetydata·data.go.kr 키 미설정 — 가로등 라이브 갱신 건너뜀(파일 적재분 유지)")
        }
        log.info(
            "안심시설 라이브 갱신 완료: {}",
            result.entries.joinToString(", ") { (type, count) -> "${type.cdnType}=${count ?: "유지"}" }
                .ifEmpty { "대상 없음" },
        )
        refreshStatusStore?.record(result, System.currentTimeMillis())
        return result
    }

    // ponytail: service 레벨 계측 — datagokr resultCode·HTTP status 등 client 내부 상세는 후속. client 모듈 계측 훅(nullable recorder 주입)으로 승격 가능
    private inline fun <T> measureFetch(provider: ApiProvider, endpoint: String, fetch: () -> T?): T? =
        apiCallLogStore.measure(
            provider = provider,
            endpoint = endpoint,
            success = { it != null },
            detail = {
                if (it == null) {
                    "수집 실패 또는 0건"
                } else {
                    null
                }
            },
            block = fetch,
        )

    /**
     * 가로등 라이브 원천은 safetydata 가로등(DSSP-IF-00084) 하나다.
     * data.go.kr 전국보안등(184만건=18,395회)은 쿼터를 감당하지 못해 파이프라인 CSV/CDN 으로 넘겼다(위 주석).
     */
    private fun collectLampPoints(): List<FacilityPoint>? {
        val streetlights: List<SafetydataStreetlight> =
            measureFetch(ApiProvider.SAFETYDATA, "streetlight") { streetlightClient.fetchStreetlights() }
                ?: return null
        return streetlights.map { it.toFacilityPoint() }.distinctBy { it.lampDedupKey() }
    }

    private fun refresh(type: FacilityType, fetch: () -> List<FacilityPoint>?): Int? {
        val points: List<FacilityPoint>? = fetch()
        if (points == null) {
            log.warn("안심시설 라이브 갱신 실패(type={}) — 기존 데이터 유지", type)
            alertNotifier?.notify(
                key = "facility-refresh:${type.cdnType}",
                title = "안심시설 라이브 갱신 실패",
                detail = "type=${type.cdnType} — 수집 실패, 기존 데이터 유지",
            )
            return null
        }
        if (points.isEmpty()) {
            log.warn("안심시설 라이브 갱신 결과 0건(type={}) — 교체하지 않음(기존 데이터 유지)", type)
            alertNotifier?.notify(
                key = "facility-refresh:${type.cdnType}",
                title = "안심시설 라이브 갱신 이상(0건)",
                detail = "type=${type.cdnType} — 유효 좌표 0건, 교체하지 않고 기존 데이터 유지",
            )
            return null
        }
        return liveStore.replace(type, points)
    }
}

private fun DataGoKrPoint.toFacilityPoint(type: FacilityType): FacilityPoint? {
    val point: GeoPoint = GeoPoint.orNull(lat, lng) ?: return null
    return FacilityPoint(
        type = type,
        lat = point.lat,
        lng = point.lng,
        name = name,
        sigungu = sigungu,
        address = address,
    )
}

private fun SafemapPoliceFacility.toFacilityPoint(): FacilityPoint = FacilityPoint(
    type = FacilityType.POLICE,
    lat = lat,
    lng = lng,
    name = name,
    sigungu = sigungu,
    address = address,
    phone = phone,
)

/** 두 원천이 같은 조명을 각각 싣고 있을 수 있어 좌표로 합친다 — 중복은 밀도 점수를 부풀린다. 5자리 ≈ 1.1m. */
private const val LAMP_DEDUP_SCALE: Double = 100_000.0

private fun FacilityPoint.lampDedupKey(): Pair<Long, Long> =
    Pair(Math.round(lat * LAMP_DEDUP_SCALE), Math.round(lng * LAMP_DEDUP_SCALE))

private fun SafetydataStreetlight.toFacilityPoint(): FacilityPoint = FacilityPoint(
    type = FacilityType.LAMP,
    lat = lat,
    lng = lng,
    sigungu = sigungu,
    address = address,
)
