package io.soo.bueongi.domain.facility

import io.soo.bueongi.client.datagokr.DataGoKrFacilityClient
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 안심시설 라이브 갱신 오케스트레이션 — "평소 파일(CSV/CDN) 적재, 주기적으로 공공데이터 API 갱신"의
 * 갱신 쪽 절반이다. 클라이언트(data.go.kr) 수집 → 도메인 검증·매핑 → 저장소 인덱스 원자 교체 순으로 흐른다.
 *
 * 안전 규칙(기동 시 파일 적재분이 기준선이고, 라이브는 그것을 **개선할 때만** 반영한다):
 * - 수집 실패(null): 교체하지 않는다 — 기존 데이터 유지.
 * - 유효 좌표 0건: 교체하지 않는다 — 빈 응답으로 기존 데이터가 소리 없이 사라지는 것 방지.
 * - 종류별 독립: CCTV 실패가 비상벨 갱신을 막지 않는다.
 *
 * 라이브 대상은 WGS84 를 직접 주는 서비스 3종(CCTV·비상벨·여성안심지킴이집)이다 — 좌표 변환·지오코딩이
 * 필요한 나머지 종류는 파이프라인(월 1회 cdn/ 산출) 담당으로 남긴다(중복 구현 방지).
 */
@Service
class FacilityRefreshService(
    private val dataGoKrClient: DataGoKrFacilityClient,
    private val liveStore: FacilityLiveStore,
) {
    private val log = LoggerFactory.getLogger(FacilityRefreshService::class.java)

    /** 인증키가 설정돼 있어 라이브 갱신이 동작 가능한지. false 면 호출측이 갱신 자체를 건너뛴다. */
    val isEnabled: Boolean
        get() = dataGoKrClient.isEnabled

    /**
     * 지원 종류 전부를 갱신한다. 반환: 종류별 교체 건수(실패/건너뜀은 null) — 로그·테스트 판정용.
     */
    fun refreshAll(): Map<FacilityType, Int?> {
        val result = mapOf(
            FacilityType.CCTV to refresh(FacilityType.CCTV) { dataGoKrClient.fetchCctv() },
            FacilityType.BELL to refresh(FacilityType.BELL) { dataGoKrClient.fetchEmergencyBells() },
            FacilityType.SAFEHOUSE to refresh(FacilityType.SAFEHOUSE) { dataGoKrClient.fetchSafehouses() },
        )
        log.info(
            "안심시설 라이브 갱신 완료: cctv={}, bell={}, safehouse={}",
            result[FacilityType.CCTV] ?: "유지",
            result[FacilityType.BELL] ?: "유지",
            result[FacilityType.SAFEHOUSE] ?: "유지",
        )
        return result
    }

    private fun refresh(type: FacilityType, fetch: () -> List<DataGoKrPoint>?): Int? {
        val fetched = fetch()
        if (fetched == null) {
            log.warn("안심시설 라이브 갱신 실패(type={}) — 기존 데이터 유지", type)
            return null
        }
        val points = fetched.mapNotNull { it.toFacilityPoint(type) }
        if (points.isEmpty()) {
            log.warn("안심시설 라이브 갱신 결과 0건(type={}, 원천 {}행) — 교체하지 않음(기존 데이터 유지)", type, fetched.size)
            return null
        }
        return liveStore.replace(type, points)
    }
}

/** 클라이언트 행 → 저장 모델. 좌표 범위(±90/±180)를 여기(도메인 경계)서 검증하고 벗어나면 버린다. */
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
