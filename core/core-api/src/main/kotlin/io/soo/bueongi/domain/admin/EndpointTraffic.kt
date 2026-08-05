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

/** 엔드포인트(메서드+정규화경로)별 트래픽 1건. */
data class EndpointTraffic(
    /** HTTP 메서드: GET | POST | … */
    val method: String,
    /** 정규화된 경로 템플릿: 예 `/share/{token}/location`. */
    val endpoint: String,
    /** 창 내 호출 수(clientId 유무 무관 전량). */
    val count: Int,
    /** 성공률 0.0~1.0 — status 가 2xx·3xx 인 비율. */
    val successRate: Double,
    /** 평균 소요시간(ms, 반올림). */
    val avgDurationMs: Long,
    /** 이 엔드포인트 그룹 내 소요시간 95 분위수(ms, nearest-rank). 빈 그룹은 없음(groupBy 결과라 최소 1건). */
    val p95DurationMs: Long,
    /** 이 엔드포인트를 부른 고유 익명 클라이언트 수(clientId null 제외). */
    val distinctClients: Int,
)
