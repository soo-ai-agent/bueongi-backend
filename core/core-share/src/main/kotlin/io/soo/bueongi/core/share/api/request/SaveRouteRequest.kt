package io.soo.bueongi.core.share.api.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.share.domain.error.InvalidRouteMetaException
import io.soo.bueongi.storage.db.share.model.StoredCoord
import io.soo.bueongi.storage.db.share.model.StoredRoute
import io.soo.bueongi.storage.db.share.model.StoredRouteDestination
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
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val path: List<@Valid LatLng> = emptyList(),
    @field:Valid
    @field:Size(max = MAX_ROUTE_MARKERS)
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val markers: List<@Valid RouteMarkerRequest> = emptyList(),
    @field:NotNull
    @field:Min(0)
    @param:JsonProperty("total_distance_m")
    val totalDistanceMeters: Int? = null,
    @field:NotNull
    @field:Min(0)
    @param:JsonProperty("total_time_s")
    val totalTimeSeconds: Int? = null,
    @field:NotNull
    val type: String? = null,
) {
    fun toStoredRoute(): StoredRoute {
        val dest: NamedLatLng = requireNotNull(destination) { "destination 은 @NotNull 검증을 통과했어야 한다" }
        val routeType: String = requireNotNull(type) { "type 은 @NotNull 검증을 통과했어야 한다" }
        if (routeType !in ROUTE_TYPE_VALUES) {
            throw InvalidRouteMetaException(
                field = "type",
                message = "type 은 ${ROUTE_TYPE_VALUES.joinToString("|")} 중 하나여야 합니다.",
                rejectedValue = routeType,
            )
        }
        return StoredRoute(
            destination =
                StoredRouteDestination(
                    name = dest.name,
                    lat = dest.lat,
                    lng = dest.lng,
                ),
            path =
                path.map {
                    StoredCoord(lat = it.lat, lng = it.lng)
                },
            markers = markers.map(RouteMarkerRequest::toStoredMarker),
            totalDistanceMeters = requireNotNull(totalDistanceMeters),
            totalTimeSeconds = requireNotNull(totalTimeSeconds),
            type = routeType,
        )
    }

    companion object {
        const val MAX_ROUTE_PATH_POINTS: Int = 4000
        const val MAX_ROUTE_MARKERS: Int = 300

        // RouteType.of 는 모르는 값을 SAFE 로 받아 줘서 쓸 수 없고, 여기는 422 로 거절할 값 목록이다.
        val ROUTE_TYPE_VALUES: Set<String> = RouteType.entries.map(RouteType::apiType).toSet()
    }
}
