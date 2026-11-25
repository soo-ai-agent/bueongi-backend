package io.soo.bueongi.core.admin.safetyspot.csv

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore

internal class FakeLiveStore : SafetySpotLiveStore {
    var lastType: SafetySpotType? = null
    var lastPoints: List<SafetySpotPoint>? = null

    override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {}

    override fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>) {
        lastType = type
        lastPoints = points
    }
}
