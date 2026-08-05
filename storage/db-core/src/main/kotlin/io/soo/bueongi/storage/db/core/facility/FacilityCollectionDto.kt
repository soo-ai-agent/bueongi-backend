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

/** CDN 컬렉션 파일 래퍼. 미사용 메타(schemaVersion/sources 등)는 무시. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FacilityCollectionDto(
    val type: String? = null,
    val items: List<FacilityItemDto> = emptyList(),
)
