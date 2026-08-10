package io.soo.bueongi.domain.share

import io.soo.bueongi.storage.db.core.share.InMemoryLocationStore

internal const val BASE_URL: String = "http://localhost:8119"

abstract class ShareServiceTestSupport {

    protected val clock: MutableTestClock = MutableTestClock()
    protected val store: InMemoryLocationStore = InMemoryLocationStore(clock)
    protected val service: ShareService = ShareService(store, clock, BASE_URL)

    protected fun report(lat: Double = 37.5, lng: Double = 127.0, remainingM: Double? = null, remainingS: Double? = null) =
        LocationReport(lat = lat, lng = lng, remainingM = remainingM, remainingS = remainingS)
}
