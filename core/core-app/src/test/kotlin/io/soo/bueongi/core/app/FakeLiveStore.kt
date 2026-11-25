package io.soo.bueongi.core.app

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore

internal class FakeLiveStore : SafetySpotLiveStore {
    override fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>) {}

    override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {}
}
