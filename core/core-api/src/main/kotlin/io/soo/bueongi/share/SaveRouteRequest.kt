package io.soo.bueongi.share

import io.soo.bueongi.api.v1.request.LatLng
import io.soo.bueongi.api.v1.request.NamedLatLng
import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.support.error.InvalidRouteMetaException
import io.soo.bueongi.storage.db.core.share.StoredCoord
import io.soo.bueongi.storage.db.core.share.StoredMarker
import io.soo.bueongi.storage.db.core.share.StoredRoute
import io.soo.bueongi.storage.db.core.share.StoredRouteDestination
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SaveRouteRequest(
    @JsonProperty("owner_secret")
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
    @JsonProperty("total_distance_m")
    val totalDistanceM: Int? = null,
    @field:Min(0)
    @JsonProperty("total_time_s")
    val totalTimeS: Int? = null,
    val type: String? = null,
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
