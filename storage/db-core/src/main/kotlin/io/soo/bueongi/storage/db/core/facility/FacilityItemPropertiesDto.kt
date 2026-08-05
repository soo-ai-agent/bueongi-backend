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

/** CDN item.properties 중 표시용 메타(CCTV 용도/카메라대수). 그 외 키는 무시. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FacilityItemPropertiesDto(
    val purpose: String? = null,
    val cameraCount: Int? = null,
)
