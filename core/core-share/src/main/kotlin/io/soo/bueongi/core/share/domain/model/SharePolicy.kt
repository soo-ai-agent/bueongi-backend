package io.soo.bueongi.core.share.domain.model

import io.soo.bueongi.core.enums.ShareProgressStatus
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredLocation
import io.soo.bueongi.storage.db.share.model.StoredRouteDestination
import java.time.Instant

const val DEFAULT_EXPIRES_HOURS: Int = 1

const val MISSED_ARRIVAL_GRACE_SECONDS: Long = 60L

const val OVERDUE_SECONDS: Long = 300L

const val LOCATION_STOPPED_SECONDS: Long = 90L

// 앱의 자동 도착 판정 반경(useArrivalDetection 의 ARRIVAL_METERS)과 같은 값.
const val ARRIVAL_RADIUS_METERS: Int = 30

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

// 앱을 꺼서 도착 보고가 없어도, 마지막 위치가 목적지 근처면 도착한 것으로 본다.
fun ShareSession.isLikelyArrived(): Boolean {
    val destination: StoredRouteDestination = route?.destination ?: return false
    val reported: StoredLocation = location ?: return false
    // 좌표만 보면 목적지를 지나치는 순간 멈춘 것도 도착으로 읽힌다 — 경로상 남은 거리가 보고돼 있으면 그것도 반경 안이어야 한다.
    val remainingMeters: Double? = reported.remainingMeters
    if (remainingMeters != null && remainingMeters > ARRIVAL_RADIUS_METERS) {
        return false
    }
    val lastPoint: GeoPoint = GeoPoint(reported.lat, reported.lng)
    val destinationPoint: GeoPoint = GeoPoint(destination.lat, destination.lng)
    return lastPoint.distanceMetersTo(destinationPoint) <= ARRIVAL_RADIUS_METERS
}

fun ShareSession.progressStatus(now: Instant): ShareProgressStatus {
    if (!isMissedArrivalSuspect(now)) {
        return ShareProgressStatus.IN_PROGRESS
    }
    if (isLikelyArrived()) {
        return ShareProgressStatus.ARRIVAL_LIKELY
    }
    return ShareProgressStatus.MISSED_ARRIVAL_SUSPECT
}
