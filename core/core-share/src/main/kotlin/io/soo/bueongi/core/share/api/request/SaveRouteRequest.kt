package io.soo.bueongi.core.share.api.request

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.share.domain.error.InvalidRouteMetaException
import io.soo.bueongi.storage.db.core.share.model.StoredCoord
import io.soo.bueongi.storage.db.core.share.model.StoredRoute
import io.soo.bueongi.storage.db.core.share.model.StoredRouteDestination
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class SaveRouteRequest(
    @param:JsonProperty("owner_secret")
    val ownerSecret: String? = null,
    @field:NotNull @field:Valid
    val destination: NamedLatLng?,
    @field:Valid
    @field:Size(max = MAX_ROUTE_PATH_POINTS)
    val path: List<@Valid LatLng>? = null,
    @field:Valid
    @field:Size(max = MAX_ROUTE_MARKERS)
    val markers: List<@Valid RouteMarkerRequest>? = null,
    @field:Min(0)
    @param:JsonProperty("total_distance_m")
    val totalDistanceM: Int? = null,
    @field:Min(0)
    @param:JsonProperty("total_time_s")
    val totalTimeS: Int? = null,
    val type: String? = null,
    val provenance: String? = null,
) {
    fun toStoredRoute(): StoredRoute {
        val dest: NamedLatLng = requireNotNull(destination) { "destination 은 @NotNull 검증을 통과했어야 한다" }
        validateRouteMeta()
        return StoredRoute(
            destination =
                StoredRouteDestination(
                    name = dest.name,
                    lat = requireNotNull(dest.lat) { "destination.lat 은 검증을 통과했어야 한다" },
                    lng = requireNotNull(dest.lng) { "destination.lng 도 동일" },
                ),
            path =
                path.orEmpty().map {
                    StoredCoord(lat = requireNotNull(it.lat), lng = requireNotNull(it.lng))
                },
            markers = markers.orEmpty().map(RouteMarkerRequest::toStoredMarker),
            totalDistanceM = totalDistanceM,
            totalTimeS = totalTimeS,
            type = type,
            provenance = provenance,
        )
    }

    private fun validateRouteMeta() {
        if (type != null && type !in ROUTE_TYPE_VALUES) {
            throw InvalidRouteMetaException(
                field = "type",
                message = "type 은 ${ROUTE_TYPE_VALUES.joinToString("|")} 중 하나여야 합니다.",
                rejectedValue = type,
            )
        }
        if (provenance != null && provenance.length > MAX_ROUTE_PROVENANCE_LENGTH) {
            throw InvalidRouteMetaException(
                field = "provenance",
                message = "provenance 는 최대 ${MAX_ROUTE_PROVENANCE_LENGTH}자까지 입력할 수 있습니다.",
                rejectedValue = provenance.length,
            )
        }
    }

    companion object {
        const val MAX_ROUTE_PATH_POINTS: Int = 4000
        const val MAX_ROUTE_MARKERS: Int = 300
        const val MAX_ROUTE_PROVENANCE_LENGTH: Int = 200

        // RouteType.of 는 모르는 값을 SAFE 로 받아 줘서 쓸 수 없고, 여기는 422 로 거절할 값 목록이다.
        val ROUTE_TYPE_VALUES: Set<String> = RouteType.entries.map(RouteType::apiType).toSet()
    }
}
