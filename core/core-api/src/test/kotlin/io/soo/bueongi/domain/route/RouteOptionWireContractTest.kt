package io.soo.bueongi.domain.route

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * RouteOption 직렬화(wire) 계약 고정 — 프론트가 소비하는 JSON 필드명을 못 박는다.
 *
 * path/steps 는 하위 모듈 소유 타입([GeoPoint]/[NavStep])을 그대로 직렬화하므로, 그 모듈에서
 * 프로퍼티를 리네임하면 컴파일은 통과해도 프론트 계약이 조용히 깨진다. 이 테스트가 그 변경을
 * 빌드 실패로 표면화한다(하위 모듈 KDoc 의 "리네임 금지" 경고와 짝).
 */
class RouteOptionWireContractTest {

    private val mapper = ObjectMapper()

    @Test
    fun `RouteOption JSON 필드명은 프론트 계약 그대로다(path·steps 포함)`() {
        val option = RouteOption(
            id = "tmap-0-0",
            name = "안심 경로",
            time = "10분",
            dist = "700m",
            desc = "안전 점수 80점",
            tags = listOf(RouteTag("안심", "mint")),
            type = "safe",
            path = listOf(GeoPoint(37.5, 127.0)),
            steps = listOf(NavStep(index = 0, lat = 37.5, lng = 127.0, description = "출발", turnType = 200, distanceM = 10, timeS = 8, pointType = "SP")),
            score = 80,
            markers = listOf(FacilityPoi(type = "cctv", x = 50, y = 50, lat = 37.5, lng = 127.0)),
            breakdown = ScoreBreakdown(routeKm = 0.7, cctvDensity = 4.5, lampDensity = 0.0, bellDensity = 0.0, safehouseCount = 0, policeCount = 0, safePathOverlap = 0.0),
            provenance = DataProvenance(kind = "live", basedOn = "2026.06", origin = "공공데이터포털"),
        )
        val json = mapper.readTree(mapper.writeValueAsString(option))

        // 최상위 필드(프론트 MockRoute + safe-compare 확장 필드).
        assertThat(json.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "id", "name", "time", "dist", "desc", "tags", "type",
            "path", "steps", "score", "markers", "breakdown", "provenance",
        )
        // path 좌표 필드명(storage GeoPoint 프로퍼티명이 곧 계약).
        assertThat(json.at("/path/0").fieldNames().asSequence().toSet()).containsExactlyInAnyOrder("lat", "lng")
        // steps 필드명(client-tmap NavStep 프로퍼티명이 곧 계약 — 프론트 utils/tmap.ts NavStep 동형).
        assertThat(json.at("/steps/0").fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "index", "lat", "lng", "description", "turnType", "distanceM", "timeS", "pointType",
        )
        // tags·breakdown·provenance 필드명.
        assertThat(json.at("/tags/0").fieldNames().asSequence().toSet()).containsExactlyInAnyOrder("text", "variant")
        assertThat(json.at("/breakdown").fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "routeKm", "cctvDensity", "lampDensity", "bellDensity", "safehouseCount", "policeCount", "safePathOverlap",
        )
        assertThat(json.at("/provenance").fieldNames().asSequence().toSet()).containsExactlyInAnyOrder("kind", "basedOn", "origin")
    }

    @Test
    fun `폴백 응답(확장 필드 null)은 NON_NULL 로 기존 shape 를 유지한다`() {
        val fallback = RouteOption(
            id = "1", name = "추천 경로", time = "7분", dist = "460m", desc = "d", tags = emptyList(), type = "safe",
        )
        val json = mapper.readTree(mapper.writeValueAsString(fallback))
        assertThat(json.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "id", "name", "time", "dist", "desc", "tags", "type",
        )
    }
}
