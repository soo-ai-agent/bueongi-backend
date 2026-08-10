package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

data class SafetydataStreetlight(
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val sigungu: String? = null,
)
