package io.soo.bueongi.core.route

import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.core.route.domain.safety.service.SafetyWeightsCatalog
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

fun nightScorer(source: SafetySpotSource): SafetyScorer =
    SafetyScorer(source, SafetyWeightsCatalog(), Clock.fixed(FIXED_NIGHT, ZoneOffset.UTC))

private val FIXED_NIGHT: Instant = Instant.parse("2026-08-15T13:00:00Z")
