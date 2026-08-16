package io.soo.bueongi.core.share.api.request

import io.soo.bueongi.core.share.domain.model.ROUTE_MARKER_TYPE_PATTERN

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

/**
 * 부가 정보는 안심시설 종류마다 있고 없다.
 */
data class RouteMarkerRequest(
    @field:NotBlank
    @field:Pattern(regexp = ROUTE_MARKER_TYPE_PATTERN)
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val type: String?,
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val lng: Double?,
    // 서버가 나중에 채우므로 받을 때는 null 이 가능함
    val x: Double? = null,
    // 서버가 나중에 채우므로 받을 때는 null 이 가능함
    val y: Double? = null,
    // 원천에 값이 없으면 null 이 가능함
    val name: String? = null,
    // CCTV 가 아닌 시설은 null 이 가능함
    val purpose: String? = null,
    @param:JsonProperty("camera_count")
    // CCTV 가 아닌 시설은 null 이 가능함
    val cameraCount: Int? = null,
    // 지구대가 아닌 시설은 null 이 가능함
    val address: String? = null,
    // 지구대가 아닌 시설은 null 이 가능함
    val phone: String? = null,
)
