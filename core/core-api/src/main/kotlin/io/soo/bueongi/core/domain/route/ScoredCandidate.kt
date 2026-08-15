package io.soo.bueongi.core.domain.route

import io.soo.bueongi.core.domain.facility.SafetyScore

internal data class ScoredCandidate(val candidate: RouteCandidate, val score: SafetyScore)
