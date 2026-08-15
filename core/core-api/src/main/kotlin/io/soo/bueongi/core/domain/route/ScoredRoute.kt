package io.soo.bueongi.core.domain.route

import io.soo.bueongi.core.enums.RouteType

import io.soo.bueongi.core.domain.facility.SafetyScore

internal data class ScoredRoute(val candidate: RouteCandidate, val score: SafetyScore, val type: RouteType)
