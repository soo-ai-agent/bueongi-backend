package io.soo.bueongi.core.common.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin

data class NamedLatLng
    @JsonCreator
    constructor(
        @field:DecimalMin("-90.0")
        @field:DecimalMax("90.0")
        @param:JsonProperty(value = "lat", required = true)
        val lat: Double,
        @field:DecimalMin("-180.0")
        @field:DecimalMax("180.0")
        @param:JsonProperty(value = "lng", required = true)
        val lng: Double,
        @param:JsonProperty("name") val name: String = "",
    )
