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

@Service
class FacilityRefreshService(
    private val dataGoKrClient: DataGoKrFacilityClient,
    private val safemapClient: SafemapPoliceClient,
    private val streetlightClient: SafetydataStreetlightClient,
    private val liveStore: FacilityLiveStore,
    private val alertNotifier: AlertNotifier? = null,
    private val apiCallLogStore: ApiCallLogStore? = null,
    private val refreshStatusStore: FacilityRefreshStatusStore? = null,
) {
    private val log: Logger = LoggerFactory.getLogger(FacilityRefreshService::class.java)

    val isEnabled: Boolean
        get() = dataGoKrClient.isEnabled || safemapClient.isEnabled || streetlightClient.isEnabled

    fun refreshAll(): Map<FacilityType, Int?> {
        val result: LinkedHashMap<FacilityType, Int?> = LinkedHashMap<FacilityType, Int?>()
        if (dataGoKrClient.isEnabled) {
            result[FacilityType.CCTV] = refresh(FacilityType.CCTV) {
                measureFetch(ApiProvider.DATAGOKR, "cctv") { dataGoKrClient.fetchCctv() }
                    ?.mapNotNull { it.toFacilityPoint(FacilityType.CCTV) }
            }
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
        if (safemapClient.isEnabled) {
            result[FacilityType.POLICE] = refresh(FacilityType.POLICE) {
                measureFetch(ApiProvider.SAFEMAP, "police") { safemapClient.fetchPoliceFacilities() }
                    ?.map { it.toFacilityPoint() }
            }
        } else {
            log.info("safemap 키 미설정 — 치안시설(파출소) 라이브 갱신 건너뜀(파일 적재분 유지)")
        }
        if (streetlightClient.isEnabled || dataGoKrClient.isEnabled) {
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
     * 가로등 원천은 둘 — safetydata 가로등(DSSP-IF-00084)과 data.go.kr 전국보안등 표준데이터.
     * 활성 원천 중 하나라도 수집에 실패하면 전체를 포기한다. 성공분만으로 교체하면 커버리지가 되레 줄기 때문이다.
     */
    private fun collectLampPoints(): List<FacilityPoint>? {
        val collected: ArrayList<FacilityPoint> = ArrayList<FacilityPoint>()
        if (streetlightClient.isEnabled) {
            val streetlights: List<SafetydataStreetlight> =
                measureFetch(ApiProvider.SAFETYDATA, "streetlight") { streetlightClient.fetchStreetlights() }
                    ?: return null
            streetlights.mapTo(collected) { it.toFacilityPoint() }
        }
        if (dataGoKrClient.isEnabled) {
            val securityLamps: List<DataGoKrPoint> =
                measureFetch(ApiProvider.DATAGOKR, "security-lamp") { dataGoKrClient.fetchSecurityLamps() }
                    ?: return null
            securityLamps.mapNotNullTo(collected) { it.toFacilityPoint(FacilityType.LAMP) }
        }
        return collected.distinctBy { it.lampDedupKey() }
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
