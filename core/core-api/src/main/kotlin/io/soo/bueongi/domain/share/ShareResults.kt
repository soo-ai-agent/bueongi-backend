package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/**
 * 위치 공유 도메인 결과·정책 상수. 결과 data class 는 도메인 결과이자 프론트 응답 계약(snake_case)이다 —
 * 동일 필드의 응답 DTO 를 한 벌 더 두는 이중 정의 대신 단일 출처로 유지한다(RouteOption 과 같은 결정).
 */

/** 공유 만료 시간(시) 허용 범위 = 1~3시간. 무기한 추적 방지(안심앱 프라이버시) — 켜둔 채 잊어도 반드시 만료된다. */
const val MIN_EXPIRES_HOURS: Int = 1

/** 공유 만료 시간 상한(시) = 3시간. MVP 계약 상한. */
const val MAX_EXPIRES_HOURS: Int = 3

/** expires_in_hours 미지정 시 기본(시) = 1시간. 통상 귀가 1회를 덮되 가장 보수적인(짧은) 만료. */
const val DEFAULT_EXPIRES_HOURS: Int = 1

/** 경로선 좌표열 상한(점). 도보 귀가 경로 한 건으로 충분히 넉넉하되, 과도한 페이로드(메모리/직렬화 비용)는 막는다. */
const val MAX_ROUTE_PATH_POINTS: Int = 4000

/** 경로변 안심시설 마커 상한(개). 회랑 내 시설로 충분하되, 과도한 페이로드를 막는다. */
const val MAX_ROUTE_MARKERS: Int = 300

/** 허용하는 마커 유형(정규식). start/end 는 프론트가 이미 제외해 보내므로 여기선 안심시설 유형만 받는다. */
const val ROUTE_MARKER_TYPE_PATTERN: String = "cctv|bell|store|police|safehouse|lamp"

/**
 * 경로 종류 허용 집합(safe|fast|main). 선택 필드라 null(미전송)은 허용하고, 값이 있으면 이 집합만 통과시킨다 —
 * 벗어나면 [io.soo.bueongi.support.error.InvalidRouteMetaException]→422. 도메인 [io.soo.bueongi.domain.route.RouteType]
 * 와 값이 같지만, 그쪽 from() 은 미상을 SAFE 로 관용 처리하므로(엄격 거부가 목적인 여기와 상반) 재사용하지 않고 명시 집합을 둔다.
 */
val ROUTE_TYPE_VALUES: Set<String> = setOf("safe", "fast", "main")

/** 시설 데이터 출처 문구(provenance) 최대 길이(자). 초과 시 422 — 과도한 페이로드/오용을 막되 통상 문구는 넉넉히 담는다. */
const val MAX_ROUTE_PROVENANCE_LENGTH: Int = 200

/**
 * 미도착 감시(서버 워치독) 정책. 예정 도착 시각 = 공유 생성 + ETA + [MISSED_ARRIVAL_GRACE_SECONDS].
 * 그 시각을 [MISSED_ARRIVAL_OVERDUE_SECONDS] 넘겨 초과하고 위치가 [MISSED_ARRIVAL_STOPPED_SECONDS] 이상
 * 멈춰 있으면 '미도착 의심'으로 본다. GRACE 는 E(클라 포그라운드 체크인)와 일관 — 정시 도착 오알림 방지.
 * (STOPPED 는 보호자 staleness 30초보다 크게 잡아 일시 blip 이 아닌 '정지'만 잡는다.)
 */
const val MISSED_ARRIVAL_GRACE_SECONDS: Long = 60L
const val MISSED_ARRIVAL_OVERDUE_SECONDS: Long = 300L
const val MISSED_ARRIVAL_STOPPED_SECONDS: Long = 90L











/** 저장 모델([StoredRoute]) → 보호자 응답([RouteContext]) 매핑. */
fun StoredRoute.toRouteContext(): RouteContext = RouteContext(
    destination = RouteDestination(name = destination.name, lat = destination.lat, lng = destination.lng),
    path = path.map { RoutePoint(lat = it.lat, lng = it.lng) },
    markers = markers.map { marker ->
        RouteMarker(
            type = marker.type,
            lat = marker.lat,
            lng = marker.lng,
            x = marker.x,
            y = marker.y,
            name = marker.name,
            purpose = marker.purpose,
            cameraCount = marker.cameraCount,
            address = marker.address,
            phone = marker.phone,
        )
    },
    totalDistanceM = totalDistanceM,
    totalTimeS = totalTimeS,
    type = type,
    provenance = provenance,
)
