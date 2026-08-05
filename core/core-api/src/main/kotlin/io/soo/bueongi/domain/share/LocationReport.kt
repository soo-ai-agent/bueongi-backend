package io.soo.bueongi.domain.share

import io.soo.bueongi.storage.db.core.share.LocationStore
import io.soo.bueongi.storage.db.core.share.ShareSession
import io.soo.bueongi.storage.db.core.share.StoredLocation
import io.soo.bueongi.storage.db.core.share.StoredRoute
import io.soo.bueongi.support.error.ShareSessionNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** 위치 보고(도메인 입력) — API 경계 검증을 통과한 비null 좌표 + 선택 잔여 거리/시간. */
data class LocationReport(
    val lat: Double,
    val lng: Double,
    val remainingM: Double? = null,
    val remainingS: Double? = null,
)
