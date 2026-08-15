package io.soo.bueongi.core.api.controller.v1.response

import io.soo.bueongi.core.domain.facility.NearestPolice

data class NearestPoliceResponse(val items: List<NearestPolice>)
