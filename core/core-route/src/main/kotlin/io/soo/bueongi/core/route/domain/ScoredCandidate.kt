package io.soo.bueongi.core.route.domain

import io.soo.bueongi.core.route.domain.safety.SafetyScore

internal data class ScoredCandidate(val candidate: RouteCandidate, val score: SafetyScore)
