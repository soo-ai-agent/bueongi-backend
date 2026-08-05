package io.soo.bueongi.domain.admin

import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_CAP
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 원천별 상태 요약 1건. */
data class ProviderStatus(
    /** 원천 이름 — [ApiProvider] 의 이름 문자열(KAKAO|TMAP|DATAGOKR|SAFEMAP). 응답 계약이라 String 으로 낸다. */
    val provider: String,
    val total: Int,
    val successCount: Int,
    /** 성공률 0.0~1.0. total=0 이면 0.0. */
    val successRate: Double,
    /** 평균 소요시간(ms, 반올림). total=0 이면 0. */
    val avgDurationMs: Long,
    /** 이 원천 그룹 내 소요시간 95 분위수(ms, nearest-rank). total=0 이면 0. */
    val p95DurationMs: Long,
    val lastSuccessAtMs: Long?,
    val lastFailureAtMs: Long?,
    /** 오늘(Asia/Seoul 자정 이후) 호출 수 — 쿼터 계측. */
    val todayCount: Int,
    /** 일일 쿼터 상한(없으면 null). */
    val quotaLimit: Int?,
    /** todayCount 가 상한의 80% 이상이면 경고. */
    val quotaWarning: Boolean,
    /**
     * 오늘 경과시간 기준 시간당 쿼터 소진율(호출/시간). 쿼터 있는 원천(DATAGOKR)만, 없으면 null.
     * 선형 외삽 근사 — 자정~현재 평균 속도가 유지된다는 가정.
     */
    val quotaBurnPerHour: Double?,
    /** 현재 소진율이 유지될 때 오늘 마감 예상 총 호출 수(반올림). 쿼터 없으면 null. */
    val quotaProjectedTodayTotal: Int?,
    /** 이 원천의 최근 24시간 시간별 호출빈도 — 전체 [ApiStatusSummary.hourlyTrend] 와 같은 경계·같은 버킷. */
    val hourlyCalls: List<HourlyBucket>,
)
