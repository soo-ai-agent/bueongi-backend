package io.soo.bueongi.core.route.domain

import io.soo.bueongi.core.facility.domain.SafetyScore

internal data class ScoredCandidate(val candidate: RouteCandidate, val score: SafetyScore)
