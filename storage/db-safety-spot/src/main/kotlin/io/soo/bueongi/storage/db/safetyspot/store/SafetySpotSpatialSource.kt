package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotFreshness

interface SafetySpotSpatialSource {
    fun index(type: SafetySpotType): GridSpatialIndex

    val safePaths: List<SafePath>

    val freshness: SafetySpotFreshness?
        get() = null

    /** 한 종류의 출처별 조각 스냅샷 — 순회 중 교체가 끼어도 한 응답은 한 시점만 본다. 구현이 없으면 전부 파일 출처로 본다. */
    fun sources(type: SafetySpotType): SafetySpotSources {
        return SafetySpotSources.EMPTY.withFiles(index(type).all)
    }
}
