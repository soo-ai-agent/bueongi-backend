package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/** items 는 배열/`{item:...}` 두 형태가 있어 [JsonNode] 로 받고 클라이언트가 정규화한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGoKrBody(val items: JsonNode? = null, val totalCount: Long? = null)
