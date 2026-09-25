package io.soo.bueongi.client.datagokr.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrBody(val items: JsonNode? = null, val totalCount: Long? = null)
