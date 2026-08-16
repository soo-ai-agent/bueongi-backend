package io.soo.bueongi.core.share.api.request

import io.soo.bueongi.core.share.domain.model.MAX_ROUTE_MARKERS
import io.soo.bueongi.core.share.domain.model.MAX_ROUTE_PATH_POINTS
import io.soo.bueongi.core.share.domain.model.MAX_ROUTE_PROVENANCE_LENGTH
import io.soo.bueongi.core.share.domain.model.ROUTE_TYPE_VALUES

import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.share.domain.error.InvalidRouteMetaException
import io.soo.bueongi.storage.db.core.share.model.StoredCoord
import io.soo.bueongi.storage.db.core.share.model.StoredMarker
import io.soo.bueongi.storage.db.core.share.model.StoredRoute
import io.soo.bueongi.storage.db.core.share.model.StoredRouteDestination
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class SaveRouteRequest(
    @param:JsonProperty("owner_secret")
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val ownerSecret: String? = null,
    @field:NotNull @field:Valid
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val destination: NamedLatLng?,
    @field:Valid
    @field:Size(max = MAX_ROUTE_PATH_POINTS)
    // 목적지만 저장하면 null 이 가능함
    val path: List<@Valid LatLng>? = null,
    @field:Valid
    @field:Size(max = MAX_ROUTE_MARKERS)
    // 경로 주변에 안심시설이 없으면 null 이 가능함
    val markers: List<@Valid RouteMarkerRequest>? = null,
    @field:Min(0)
    @param:JsonProperty("total_distance_m")
    // 프론트가 아직 계산하지 않았으면 null 이 가능함
    val totalDistanceM: Int? = null,
    @field:Min(0)
    @param:JsonProperty("total_time_s")
    // 프론트가 아직 계산하지 않았으면 null 이 가능함
    val totalTimeS: Int? = null,
    // 경로 종류를 고르지 않으면 null 이 가능함
    val type: String? = null,
    // 데이터 출처를 모르면 null 이 가능함
    val provenance: String? = null,
) {
    fun toStoredRoute(): StoredRoute {
        val dest: NamedLatLng = requireNotNull(destination) { "destination 은 @NotNull 검증을 통과했어야 한다" }
        validateRouteMeta()
        return StoredRoute(
            destination = StoredRouteDestination(
                name = dest.name,
                lat = requireNotNull(dest.lat) { "destination.lat 은 검증을 통과했어야 한다" },
                lng = requireNotNull(dest.lng) { "destination.lng 도 동일" },
            ),
            path = path.orEmpty().map {
                StoredCoord(lat = requireNotNull(it.lat), lng = requireNotNull(it.lng))
            },
            markers = markers.orEmpty().map { marker ->
                StoredMarker(
                    type = requireNotNull(marker.type) { "marker.type 은 검증을 통과했어야 한다" },
                    lat = requireNotNull(marker.lat),
                    lng = requireNotNull(marker.lng),
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
    }

    private fun validateRouteMeta() {
        type?.let {
            if (it !in ROUTE_TYPE_VALUES) {
                throw InvalidRouteMetaException(
                    field = "type",
                    message = "type 은 ${ROUTE_TYPE_VALUES.joinToString("|")} 중 하나여야 합니다.",
                    rejectedValue = it,
                )
            }
        }
        provenance?.let {
            if (it.length > MAX_ROUTE_PROVENANCE_LENGTH) {
                throw InvalidRouteMetaException(
                    field = "provenance",
                    message = "provenance 는 최대 ${MAX_ROUTE_PROVENANCE_LENGTH}자까지 입력할 수 있습니다.",
                    rejectedValue = it.length,
                )
            }
        }
    }
}
