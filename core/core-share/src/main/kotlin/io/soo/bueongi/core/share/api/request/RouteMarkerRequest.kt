package io.soo.bueongi.core.share.api.request

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.share.model.StoredMarker
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class RouteMarkerRequest(
    @field:NotBlank
    @field:Pattern(regexp = ROUTE_MARKER_TYPE_PATTERN)
    val type: String?,
    @field:NotNull
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val lng: Double?,
    @field:NotNull
    val x: Double? = null,
    @field:NotNull
    val y: Double? = null,
    val name: String? = null,
    val purpose: String? = null,
    @param:JsonProperty("camera_count")
    val cameraCount: Int? = null,
    val address: String? = null,
    val phone: String? = null,
) {
    fun toStoredMarker(): StoredMarker {
        return StoredMarker(
            type = requireNotNull(type) { "marker.type 은 검증을 통과했어야 한다" },
            lat = requireNotNull(lat),
            lng = requireNotNull(lng),
            x = requireNotNull(x),
            y = requireNotNull(y),
            name = name,
            purpose = purpose,
            cameraCount = cameraCount,
            address = address,
            phone = phone,
        )
    }

    companion object {
        const val ROUTE_MARKER_TYPE_PATTERN: String = "cctv|bell|store|police|safehouse|lamp|security_lamp|pedestrian_signal"
    }
}
