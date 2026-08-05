package io.soo.bueongi.storage.db.core.facility
import io.soo.bueongi.core.enums.FacilityType

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.charset.Charset

/** safepath 좌표열의 한 점. {lat, lng} 또는 [lng, lat] 어느 쪽이든 lat/lng 키 우선. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CoordDto(
    val lat: Double? = null,
    val lng: Double? = null,
) {
    fun toGeoPoint(): GeoPoint? = GeoPoint.orNull(lat, lng)
}
