package io.soo.bueongi.core.route

import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.core.route.domain.safety.service.SafetyWeightsCatalog
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * 가중치가 밤낮에 따라 달라지므로 채점 테스트는 시각을 고정한다 —
 * 고정하지 않으면 같은 테스트가 오후에 돌 때와 밤에 돌 때 다른 점수를 본다.
 */
fun nightScorer(source: SafetySpotSpatialSource): SafetyScorer =
    SafetyScorer(source, SafetyWeightsCatalog(), Clock.fixed(FIXED_NIGHT, ZoneOffset.UTC))

/** 서울 8월 15일 22시(일몰 19:24 뒤). */
private val FIXED_NIGHT: Instant = Instant.parse("2026-08-15T13:00:00Z")
