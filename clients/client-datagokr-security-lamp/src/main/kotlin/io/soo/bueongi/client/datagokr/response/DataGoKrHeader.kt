package io.soo.bueongi.client.datagokr.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrHeader(
    @JsonSetter(nulls = Nulls.AS_EMPTY) val resultCode: String = "",
)
