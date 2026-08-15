package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.core.enums.RouteType

import io.soo.bueongi.core.route.safety.model.SafetyScore

internal data class ScoredRoute(val candidate: RouteCandidate, val score: SafetyScore, val type: RouteType)
