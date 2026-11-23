package io.soo.bueongi.core.share

import io.soo.bueongi.core.share.domain.model.LocationReport
import io.soo.bueongi.core.share.domain.service.ShareService
import io.soo.bueongi.storage.db.share.store.InMemoryShareSessionStore

internal const val BASE_URL: String = "http://localhost:8119"

abstract class ShareServiceTestSupport {
    protected val clock = MutableTestClock()
    protected val store = InMemoryShareSessionStore(clock)
    protected val service = ShareService(store, clock, BASE_URL)

    protected fun report(
        lat: Double = 37.5,
        lng: Double = 127.0,
        remainingMeters: Double? = null,
        remainingSeconds: Double? = null,
    ) = LocationReport(lat = lat, lng = lng, remainingMeters = remainingMeters, remainingSeconds = remainingSeconds)
}
