package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/**
 * 경로변 안심시설 마커 — 보호자 응답 전용. 앱 지도에 찍힌 시설(CCTV·비상벨·안심상점·지구대/파출소·
 * 여성안심지킴이집·가로등)을 보호자 페이지가 그대로 그릴 수 있게 좌표 + 표시 힌트(x/y %)와
 * 정보 카드용 부가 필드를 담는다(요청 검증은 api/v1/request 의 RouteMarkerRequest 가 담당).
 */
data class RouteMarker(
    val type: String,
    val lat: Double,
    val lng: Double,
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
