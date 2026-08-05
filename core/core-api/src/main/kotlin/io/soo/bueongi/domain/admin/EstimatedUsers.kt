package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.requestmetric.REQUEST_METRIC_CAP
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

/**
 * 예측 사용자 수 — 고유 익명 client-id 기반 **관측치**(로그인 사용자 수가 아니다).
 * clientId 없는 요청은 [today]/[last7d]/[last30d] 에서 제외되며, 그 수는 [callsWithoutClientId] 로 정직하게 노출한다.
 */
data class EstimatedUsers(
    /** 오늘(Asia/Seoul 자정 이후) 고유 clientId 수. */
    val today: Int,
    /** 최근 7일(이동 창) 고유 clientId 수. */
    val last7d: Int,
    /** 최근 30일(이동 창) 고유 clientId 수. */
    val last30d: Int,
    /** clientId 가 없어 사용자 추정에서 제외된 호출 수(정직성 지표). */
    val callsWithoutClientId: Int,
    /**
     * 오늘 활동한 clientId 중 **창(CAP) 내 최초 관측이 오늘**인 수(신규 근사).
     * 창 밖 과거 방문은 알 수 없어, 오래된 사용자가 창에서 밀려나면 신규로 잡힐 수 있는 관측 근사다.
     */
    val newToday: Int,
    /** 오늘 활동한 clientId 중 **창 내 최초 관측이 오늘 이전**인 수(재방문 근사). [today] = [newToday] + [returningToday]. */
    val returningToday: Int,
)
