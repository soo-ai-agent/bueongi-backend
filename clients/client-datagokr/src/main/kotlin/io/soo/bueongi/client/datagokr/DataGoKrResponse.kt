package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGoKrResponse(val header: DataGoKrHeader? = null, val body: DataGoKrBody? = null)
