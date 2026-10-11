package io.soo.bueongi.core.share

import io.soo.bueongi.core.share.domain.model.LocationReport
import io.soo.bueongi.core.share.domain.service.ShareService
import io.soo.bueongi.storage.db.share.store.InMemoryLocationStore

internal const val BASE_URL: String = "http://localhost:8119"

abstract class ShareServiceTestSupport {
    protected val clock = MutableTestClock()
    protected val store = InMemoryLocationStore(clock)
    protected val service = ShareService(store, clock, BASE_URL)

    protected fun report(
        lat: Double = 37.5,
        lng: Double = 127.0,
        remainingM: Double? = null,
        remainingS: Double? = null,
    ) = LocationReport(lat = lat, lng = lng, remainingM = remainingM, remainingS = remainingS)
}
