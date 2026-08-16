package io.soo.bueongi.core.share.api.request

import io.soo.bueongi.core.share.domain.model.ROUTE_MARKER_TYPE_PATTERN

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

/**
 * 빠진 값도 받아야 어느 필드가 틀렸는지 응답에 담긴다.
 * 부가 정보는 안심시설 종류마다 있고 없다.
 */
data class RouteMarkerRequest(
    @field:NotBlank
    @field:Pattern(regexp = ROUTE_MARKER_TYPE_PATTERN)
    val type: String?,
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
    val x: Double? = null,
    val y: Double? = null,
    val name: String? = null,
    val purpose: String? = null,
    @param:JsonProperty("camera_count")
    val cameraCount: Int? = null,
    val address: String? = null,
    val phone: String? = null,
)
