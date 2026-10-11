package io.soo.bueongi.core.share.domain.model

import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredLocation
import java.time.Instant

const val DEFAULT_EXPIRES_HOURS: Int = 1

const val MISSED_ARRIVAL_GRACE_SECONDS: Long = 60L

const val OVERDUE_SECONDS: Long = 300L

const val LOCATION_STOPPED_SECONDS: Long = 90L

fun ShareSession.isOverdue(now: Instant): Boolean {
    val expected: Instant = expectedArrivalAt ?: return false
    return now.isAfter(expected.plusSeconds(OVERDUE_SECONDS))
}

fun ShareSession.isLocationStopped(now: Instant): Boolean {
    val reported: StoredLocation = location ?: return true
    return now.isAfter(reported.updatedAt.plusSeconds(LOCATION_STOPPED_SECONDS))
}

fun ShareSession.isMissedArrivalSuspect(now: Instant): Boolean {
    return isOverdue(now) && isLocationStopped(now)
}
