package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.Instant

/**
 * 위치 공유 도메인 결과·정책 상수. 결과 data class 는 도메인 결과이자 프론트 응답 계약(snake_case)이다 —
 * 동일 필드의 응답 DTO 를 한 벌 더 두는 이중 정의 대신 단일 출처로 유지한다(RouteOption 과 같은 결정).
 */

/** 공유 만료 시간(시) 허용 범위 = 1~3시간. 무기한 추적 방지(안심앱 프라이버시) — 켜둔 채 잊어도 반드시 만료된다. */
const val MIN_EXPIRES_HOURS = 1

/** 공유 만료 시간 상한(시) = 3시간. MVP 계약 상한. */
const val MAX_EXPIRES_HOURS = 3

/** expires_in_hours 미지정 시 기본(시) = 1시간. 통상 귀가 1회를 덮되 가장 보수적인(짧은) 만료. */
const val DEFAULT_EXPIRES_HOURS = 1

/** 경로선 좌표열 상한(점). 도보 귀가 경로 한 건으로 충분히 넉넉하되, 과도한 페이로드(메모리/직렬화 비용)는 막는다. */
const val MAX_ROUTE_PATH_POINTS = 4000

/** 경로변 안심시설 마커 상한(개). 회랑 내 시설로 충분하되, 과도한 페이로드를 막는다. */
const val MAX_ROUTE_MARKERS = 300

/** 허용하는 마커 유형(정규식). start/end 는 프론트가 이미 제외해 보내므로 여기선 안심시설 유형만 받는다. */
const val ROUTE_MARKER_TYPE_PATTERN = "cctv|bell|store|police|safehouse|lamp"

/**
 * 경로 종류 허용 집합(safe|fast|main). 선택 필드라 null(미전송)은 허용하고, 값이 있으면 이 집합만 통과시킨다 —
 * 벗어나면 [io.soo.bueongi.support.error.InvalidRouteMetaException]→422. 도메인 [io.soo.bueongi.domain.route.RouteType]
 * 와 값이 같지만, 그쪽 from() 은 미상을 SAFE 로 관용 처리하므로(엄격 거부가 목적인 여기와 상반) 재사용하지 않고 명시 집합을 둔다.
 */
val ROUTE_TYPE_VALUES = setOf("safe", "fast", "main")

/** 시설 데이터 출처 문구(provenance) 최대 길이(자). 초과 시 422 — 과도한 페이로드/오용을 막되 통상 문구는 넉넉히 담는다. */
const val MAX_ROUTE_PROVENANCE_LENGTH = 200

/** `POST /share/create` 응답: 발급된 토큰 + 외부 메신저로 공유할 URL + 상한 만료 시각. */
data class CreateShareResponse(
    /** 공유 URL에 담기는 읽기 전용 토큰(보호자가 위치를 조회). */
    val token: String,
    /**
     * 위치 쓰기/종료에 필요한 비밀(앱만 보관, 매 전송/종료에 본문으로 전송). 공유 URL에는 포함하지 않는다.
     * 이 분리로 공유 URL이 유출돼도 조회만 가능하고 위치 조작·임의 종료는 불가하다.
     */
    @JsonProperty("owner_secret")
    val ownerSecret: String,
    @JsonProperty("share_url")
    val shareUrl: String,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
)

/** `POST /share/{token}/location`·`/route` 응답: 갱신 시각 + 공유 상한 만료. */
data class UpdateLocationResponse(
    @JsonProperty("updated_at")
    val updatedAt: Instant,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
)

/**
 * `GET /share/{token}/location` 응답(보호자 웹 5초 폴링). 200 으로만 응답하고 상태는 [expired]/위치 null 로 표현한다:
 * - 토큰 없음/만료: `{ expired: true }`(좌표 null) — 보호자 UI 가 "공유 종료/만료" 화면.
 * - 살아있으나 첫 위치 전: `{ expired: false, lat: null, lng: null }` — "위치 받는 중".
 * - 위치 있음: `{ lat, lng, updated_at, expired: false }`.
 */
data class LocationView(
    val lat: Double?,
    val lng: Double?,
    @JsonProperty("updated_at")
    val updatedAt: Instant?,
    val expired: Boolean,
    /** 목적지까지 남은 거리(미터). 앱이 보고했을 때만 채워지고, 아니면 null(보호자 UI 는 표시 생략). */
    @JsonProperty("remaining_m")
    val remainingM: Double? = null,
    /** 목적지까지 남은 예상 시간(초). [remainingM] 과 동일 규약(보고 시에만 채움). */
    @JsonProperty("remaining_s")
    val remainingS: Double? = null,
)

/**
 * `POST /share/{token}/watching` 응답(사용자 본인 = 관리자 전용). 두 가지를 함께 싣는다:
 * - [watching]: 최근 유효시간 내 보호자(뷰어) 조회가 있었는지(누군가 보는 중).
 * - 현재 공유 위치([lat]/[lng]/[updatedAt]/[expired]): 관리자가 보호자에게 보이는 화면을 '따로' 미리볼 수 있게 한다.
 *
 * 이 조회는 owner_secret 인증을 거치고 시청자로 집계되지 않으므로([ShareService.getWatchingStatus]),
 * 관리자 본인의 미리보기가 '보호자 시청 중' 알림을 만들지 않는다(읽기 토큰 GET /location 과 분리).
 * 위치 표현은 [LocationView] 와 동일 계약: 토큰 없음/만료 → expired:true(좌표 null), 첫 위치 전 → 좌표 null.
 */
data class WatchResponse(
    val watching: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
    @JsonProperty("updated_at")
    val updatedAt: Instant? = null,
    val expired: Boolean = false,
    /** 목적지까지 남은 거리(미터) — 관리자 미리보기를 보호자 화면과 일치시키려 [LocationView] 와 동일하게 싣는다. */
    @JsonProperty("remaining_m")
    val remainingM: Double? = null,
    /** 목적지까지 남은 예상 시간(초). [remainingM] 과 동일 규약. */
    @JsonProperty("remaining_s")
    val remainingS: Double? = null,
)

/**
 * 경로 좌표 1점(경로선). 요청 검증(@Valid 캐스케이드)과 응답 직렬화가 공유하는 값 타입 —
 * 범위(@DecimalMin/@DecimalMax) + 비유한(NaN/±Inf) 거부(Jackson 기본).
 */
data class RoutePoint(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
)

/** 경로 목적지(좌표 + 표시명). name 은 선택(집·회사 등 라벨), 좌표는 필수·범위 검증. */
data class RouteDestination(
    val name: String? = null,
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
)

/**
 * 경로변 안심시설 마커. 앱 지도에 찍힌 시설(CCTV·비상벨·안심상점·지구대/파출소·여성안심지킴이집·가로등)을
 * 보호자 페이지가 그대로 그릴 수 있게 좌표 + 표시 힌트(x/y %)와 정보 카드용 부가 필드를 담는다.
 * type 은 허용 집합만(@Pattern) — start/end 는 프론트가 이미 걸러 보낸다.
 */
data class RouteMarker(
    @field:NotBlank
    @field:Pattern(regexp = ROUTE_MARKER_TYPE_PATTERN)
    val type: String?,
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
    /** 화면 표시 힌트(%). 앱이 계산한 미니맵 좌표를 그대로 재현하기 위한 선택 값. */
    val x: Double? = null,
    val y: Double? = null,
    val name: String? = null,
    val purpose: String? = null,
    @JsonProperty("camera_count")
    val cameraCount: Int? = null,
    val address: String? = null,
    val phone: String? = null,
)

/**
 * `GET /share/{token}/route` 응답(보호자, 인증 없음·1회 fetch). 위치 조회([LocationView])와 같은 결로
 * 항상 200 이고 상태는 [expired]/[route] null 로 표현한다 — 토큰 없음/만료 → `{expired:true, route:null}`,
 * 살아있으나 아직 경로 미저장 → `{expired:false, route:null}`, 저장됨 → `{expired:false, route:{...}}`.
 * 이 조회는 위치 폴링(GET /location)과 달리 시청자로 집계하지 않는다.
 */
data class RouteView(
    val expired: Boolean,
    val route: RouteContext?,
)

/** 저장된 경로 컨텍스트의 보호자 응답 형태. 저장 요청과 동일 필드명(snake_case)으로 왕복한다. */
data class RouteContext(
    val destination: RouteDestination,
    val path: List<RoutePoint>,
    val markers: List<RouteMarker>,
    @JsonProperty("total_distance_m")
    val totalDistanceM: Int?,
    @JsonProperty("total_time_s")
    val totalTimeS: Int?,
    /** 경로 종류(safe|fast|main). 저장 시 함께 온 값을 그대로 에코 — 미저장이면 null(보호자 UI 표시 생략). */
    val type: String? = null,
    /** 시설 데이터 출처·기준일 문구. 저장된 값을 그대로 에코 — 미저장이면 null. */
    val provenance: String? = null,
)

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
