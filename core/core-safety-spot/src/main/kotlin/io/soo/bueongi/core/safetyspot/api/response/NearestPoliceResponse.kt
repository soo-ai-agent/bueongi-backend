package io.soo.bueongi.core.safetyspot.api.response

import io.soo.bueongi.core.safetyspot.domain.model.NearestPolice

data class NearestPoliceResponse(val items: List<NearestPolice>)
