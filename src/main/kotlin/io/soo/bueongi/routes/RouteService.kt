package io.soo.bueongi.routes

import org.springframework.stereotype.Service

/**
 * 경로 비교 서비스. 출발/도착 좌표의 직선거리에 유형별 계수를 곱해 safe/main/fast 3개 경로를 만든다.
 *
 * 외부 지도 라우팅 API 없이도 동작하는 결정론적 MVP — 같은 좌표엔 항상 같은 결과. 실측 라우팅(Tmap/Kakao)
 * 연동 시 이 서비스 내부만 교체하면 되고, 응답 계약(RouteOption: 프론트 MockRoute)은 동일하다.
 */
@Service
class RouteService {

    /** 출발→도착 직선거리를 기준으로 safe/main/fast 경로를 생성한다(추천=safe 우선 정렬). */
    fun compare(request: RouteRequest): List<RouteOption> {
        val o = request.validatedOrigin()
        val d = request.destination!!
        val straight = Geo.haversineMeters(o.lat!!, o.lng!!, d.lat!!, d.lng!!)
        return RouteType.entries.map { type ->
            val meters = straight * type.distanceFactor
            RouteOption(
                id = type.id,
                name = type.displayName,
                time = "${Geo.walkMinutes(meters)}분",
                dist = Geo.formatDistance(meters),
                desc = type.desc,
                tags = type.tags,
                type = type.apiType,
            )
        }
    }
}
