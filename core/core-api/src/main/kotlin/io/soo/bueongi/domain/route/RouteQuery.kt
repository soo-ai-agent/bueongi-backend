package io.soo.bueongi.domain.route

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.facility.GeoPoint

/**
 * 검증 완료된 경로 질의(도메인 입력). API 경계(RouteRequest.toValidatedQuery)가 출발지 확보·
 * 동일 지점·원거리 가드를 통과시킨 뒤 만들므로 좌표는 항상 유효한 비null 값이다 —
 * 도메인 서비스에서 널 단언(!!)이 사라지는 지점.
 */
data class RouteQuery(
    val origin: GeoPoint,
    val destination: GeoPoint,
    /** 목적지 표시명(선택) — Tmap 요청의 endName·안내 문구에 쓴다. */
    val destinationName: String?,
    /** 안심시설 조회(facilities) 대상 경로 유형. 미상/누락이면 SAFE. */
    val routeType: RouteType,
    /** 안심 강도 선호(safe-compare 전용). 미상/누락이면 SAFE(0.35). */
    val safetyPreference: SafetyPreference,
)
