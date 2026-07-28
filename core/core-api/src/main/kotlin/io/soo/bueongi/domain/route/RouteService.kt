package io.soo.bueongi.domain.route

import io.soo.bueongi.storage.db.core.facility.SpatialMath
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

/**
 * 경로 비교 서비스. 출발/도착 좌표의 직선거리에 유형별 계수를 곱해 safe/main/fast 3개 경로를 만든다.
 *
 * 외부 지도 라우팅 API 없이도 동작하는 결정론적 MVP — 같은 좌표엔 항상 같은 결과. 실측 라우팅(Tmap/Kakao)
 * 연동 시 이 서비스 내부만 교체하면 되고, 응답 계약(RouteOption: 프론트 MockRoute)은 동일하다.
 */
@Service
class RouteService(
    /** 추천 통계 집계(없으면 null 무해) — 순수 부수효과, 경로 산출은 불변. */
    private val routeStatsStore: RouteStatsStore? = null,
) {

    /** POST /api/routes/compare — 추천 요청을 집계(compare)한 뒤 경로를 생성한다. */
    fun compare(query: RouteQuery): List<RouteOption> {
        routeStatsStore.recordRecommendation("compare", query)
        return buildRoutes(query)
    }

    /**
     * 순수 경로 생성(집계 없음) — 출발→도착 직선거리를 기준으로 safe/main/fast 를 만든다(추천=safe 우선 정렬).
     * safe-compare 의 폴백이 이중 카운트 없이 재사용한다(추천 집계는 safe-compare 가 이미 1회 기록한다).
     */
    fun buildRoutes(query: RouteQuery): List<RouteOption> {
        val straightMeters: Double = SpatialMath.haversineMeters(query.origin, query.destination)
        return RouteType.entries.map { type ->
            val meters: Double = straightMeters * type.distanceFactor
            RouteOption(
                id = type.id,
                name = type.displayName,
                time = "${RouteFormat.walkMinutes(meters)}분",
                dist = RouteFormat.formatDistance(meters),
                desc = type.desc,
                tags = type.tags,
                type = type,
            )
        }
    }
}

/**
 * 추천 요청 1건을 집계에 기록한다(순수 부수효과). store 가 null(테스트 슬라이스 등 계측 저장소 미주입)이면 무시하고,
 * 저장소의 [RouteStatsStore.record] 는 계약상 예외를 삼키므로 이 호출은 추천 응답 흐름을 절대 깨지 않는다.
 * clients 의 접속 내역 계측([io.soo.bueongi.storage.db.core.apilog.measure])과 같은 nullable-확장 패턴을 도메인 쪽에 둔다
 * — 매핑이 도메인 입력([RouteQuery])에 의존하므로 storage 가 아닌 여기(도메인)가 올바른 위치다.
 */
internal fun RouteStatsStore?.recordRecommendation(endpoint: String, query: RouteQuery) {
    if (this == null) {
        return
    }
    record(
        RouteRecommendationLog(
            atEpochMs = System.currentTimeMillis(),
            endpoint = endpoint,
            destinationName = query.destinationName,
            preference = query.safetyPreference,
        ),
    )
}
