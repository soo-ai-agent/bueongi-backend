package io.soo.bueongi.domain.facility

import io.soo.bueongi.client.datagokr.DataGoKrFacilityClient
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.safemap.SafemapPoliceClient
import io.soo.bueongi.client.safemap.SafemapPoliceFacility
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 안심시설 라이브 갱신 오케스트레이션 — "평소 파일(CSV/CDN) 적재, 주기적으로 공공데이터 API 갱신"의
 * 갱신 쪽 절반이다. 클라이언트 수집 → 도메인 검증·매핑 → 저장소 인덱스 원자 교체 순으로 흐른다.
 *
 * 원천은 소스별로 독립 게이트된다(키 있는 것만 갱신, 없는 것은 파일 적재분 유지):
 * - data.go.kr(행안부 조회서비스·표준데이터): CCTV·비상벨·여성안심지킴이집 — `DATA_GO_KR_SERVICE_KEY`
 * - 생활안전지도(safemap, 경찰청): 치안시설(지구대·파출소·치안센터) — `SAFEMAP_SERVICE_KEY`
 * 좌표 변환·지오코딩을 파이프라인에만 맡기던 원칙에서 EPSG:5179 는 예외로 클라이언트가 자체 변환한다
 * (client-safemap [io.soo.bueongi.client.safemap.Epsg5179] — 원천이 좌표를 직접 주는 경우만).
 *
 * 안전 규칙(기동 시 파일 적재분이 기준선이고, 라이브는 그것을 **개선할 때만** 반영한다):
 * - 수집 실패(null): 교체하지 않는다 — 기존 데이터 유지.
 * - 유효 좌표 0건: 교체하지 않는다 — 빈 응답으로 기존 데이터가 소리 없이 사라지는 것 방지.
 * - 종류별 독립: 한 종류의 실패가 다른 종류의 갱신을 막지 않는다.
 */
@Service
class FacilityRefreshService(
    private val dataGoKrClient: DataGoKrFacilityClient,
    private val safemapClient: SafemapPoliceClient,
    private val liveStore: FacilityLiveStore,
    /** 운영 알림(없으면 null 무해) — 키가 있는 원천의 갱신 실패/0건만 알린다(미설정 건너뜀은 정상이라 제외). */
    private val alertNotifier: AlertNotifier? = null,
) {
    private val log = LoggerFactory.getLogger(FacilityRefreshService::class.java)

    /** 어느 원천이든 키가 있어 라이브 갱신이 동작 가능한지. false 면 호출측이 갱신 자체를 건너뛴다. */
    val isEnabled: Boolean
        get() = dataGoKrClient.isEnabled || safemapClient.isEnabled

    /**
     * 키가 설정된 원천의 지원 종류 전부를 갱신한다.
     * 반환: 종류별 교체 건수(실패/건너뜀은 null, 키 없는 원천의 종류는 미포함) — 로그·테스트 판정용.
     */
    fun refreshAll(): Map<FacilityType, Int?> {
        val result = LinkedHashMap<FacilityType, Int?>()
        if (dataGoKrClient.isEnabled) {
            result[FacilityType.CCTV] = refresh(FacilityType.CCTV) {
                dataGoKrClient.fetchCctv()?.mapNotNull { it.toFacilityPoint(FacilityType.CCTV) }
            }
            result[FacilityType.BELL] = refresh(FacilityType.BELL) {
                dataGoKrClient.fetchEmergencyBells()?.mapNotNull { it.toFacilityPoint(FacilityType.BELL) }
            }
            result[FacilityType.SAFEHOUSE] = refresh(FacilityType.SAFEHOUSE) {
                dataGoKrClient.fetchSafehouses()?.mapNotNull { it.toFacilityPoint(FacilityType.SAFEHOUSE) }
            }
        } else {
            log.info("data.go.kr 키 미설정 — CCTV·비상벨·안심지킴이집 라이브 갱신 건너뜀(파일 적재분 유지)")
        }
        if (safemapClient.isEnabled) {
            result[FacilityType.POLICE] = refresh(FacilityType.POLICE) {
                safemapClient.fetchPoliceFacilities()?.map { it.toFacilityPoint() }
            }
        } else {
            log.info("safemap 키 미설정 — 치안시설(파출소) 라이브 갱신 건너뜀(파일 적재분 유지)")
        }
        log.info(
            "안심시설 라이브 갱신 완료: {}",
            result.entries.joinToString(", ") { (type, count) -> "${type.cdnType}=${count ?: "유지"}" }
                .ifEmpty { "대상 없음" },
        )
        return result
    }

    private fun refresh(type: FacilityType, fetch: () -> List<FacilityPoint>?): Int? {
        val points = fetch()
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

/** data.go.kr 행 → 저장 모델. 좌표 범위(±90/±180)를 여기(도메인 경계)서 검증하고 벗어나면 버린다. */
private fun DataGoKrPoint.toFacilityPoint(type: FacilityType): FacilityPoint? {
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return FacilityPoint(
        type = type,
        lat = lat,
        lng = lng,
        name = name,
        sigungu = sigungu,
        address = address,
    )
}

/** safemap 치안시설 → 저장 모델. 좌표는 클라이언트가 이미 WGS84 로 변환·한국범위 검증을 마쳤다. */
private fun SafemapPoliceFacility.toFacilityPoint(): FacilityPoint = FacilityPoint(
    type = FacilityType.POLICE,
    lat = lat,
    lng = lng,
    name = name,
    sigungu = sigungu,
    address = address,
    phone = phone,
)
