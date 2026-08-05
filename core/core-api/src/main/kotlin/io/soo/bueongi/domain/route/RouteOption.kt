package io.soo.bueongi.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.facility.GeoPoint

/**
 * 경로 1건 — 도메인 결과이자 프론트 응답 계약(MockRoute 와 동일 필드: id/name/time/dist/desc/tags/type).
 * time 은 "24분", dist 는 "1.2km" 표기, type 은 'safe'|'main'|'fast'.
 *
 * safe-compare(실 Tmap + 안심 점수) 응답에서는 path/steps/score/markers 를 추가로 채운다 — 프론트가
 * 이미 이 필드들을 소비하므로 이름을 일관 유지한다. compare/facilities(폴백) 응답에서는 이들이 null 이며,
 * [JsonInclude]=NON_NULL 로 직렬화에서 빠져 기존 계약 shape 가 그대로 유지된다(추가만, 변경 없음).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RouteOption(
    val id: String,
    val name: String,
    val time: String,
    val dist: String,
    val desc: String,
    val tags: List<RouteTag>,
    val type: RouteType,
    /** 경로 라인 좌표열(WGS84 {lat,lng}). safe-compare 전용 — 프론트 지도/마커 투영의 원천. */
    val path: List<GeoPoint>? = null,
    /** 단계별 길안내(turnType/description/거리/시간). safe-compare 전용. */
    val steps: List<NavStep>? = null,
    /** 안전 점수(0~100). safe-compare 전용. */
    val score: Int? = null,
    /** 경로변 안심시설 마커(회랑 내 CCTV/안심집/비상벨 + 출발/도착). safe-compare 전용. */
    val markers: List<FacilityPoi>? = null,
    /** 안전 점수 근거(P0-4) — 회랑 내 시설 밀도/개수/겹침. safe-compare 전용. */
    val breakdown: ScoreBreakdown? = null,
    /** 데이터 출처/기준일(P0-1·P0-3). safe-compare(실데이터) 전용 — 폴백 응답엔 없어 프론트가 '예시'로 구분. */
    val provenance: DataProvenance? = null,
)
