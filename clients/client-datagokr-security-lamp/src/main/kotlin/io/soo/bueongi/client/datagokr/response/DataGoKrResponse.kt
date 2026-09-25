package io.soo.bueongi.client.datagokr.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrResponse(val header: DataGoKrHeader? = null, val body: DataGoKrBody? = null)
