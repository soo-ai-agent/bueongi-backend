package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.core.route.domain.safety.model.SafetyScore

internal data class ScoredCandidate(val candidate: RouteCandidate, val score: SafetyScore)
