package io.soo.bueongi.domain.facility

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 안심시설 라이브 갱신 트리거 2종 — 실제 갱신 흐름은 [FacilityRefreshService] 가 담당한다.
 *
 * 1. 기동 직후 1회: 파일(CSV/CDN)에 없는/낡은 종류를 즉시 보충한다. 서빙을 막지 않도록
 *    Spring 의 [TaskExecutor](부트 기본 applicationTaskExecutor)에서 돌린다(수십만 행 순회가 수 분 걸릴 수 있음).
 * 2. 주기(cron, 기본 매월 1일 04:00 KST): 공공데이터 갱신 주기(월 단위)에 맞춘 재수집.
 *
 * 키(`DATA_GO_KR_SERVICE_KEY`·`SAFEMAP_SERVICE_KEY`)가 모두 미설정이면 두 트리거 모두 건너뛴다
 * (파일 적재분 유지) — 실패 전이 없음. 한쪽만 있으면 그 원천의 종류만 갱신한다(소스별 게이트는 서비스가 담당).
 */
@Component
class FacilityRefreshScheduler(
    private val refreshService: FacilityRefreshService,
    private val taskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(FacilityRefreshScheduler::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (!refreshService.isEnabled) {
            log.info("안심시설 라이브 갱신 비활성(DATA_GO_KR_SERVICE_KEY·SAFEMAP_SERVICE_KEY 미설정) — 파일 적재분으로 동작")
            return
        }
        taskExecutor.execute {
            log.info("안심시설 라이브 갱신 시작(기동 직후 1회)")
            refreshService.refreshAll()
        }
    }

    // cron 기본값은 application.yml(bueongi.facility.refresh-cron) 한 곳에만 둔다(중복 정의 방지).
    @Scheduled(cron = "\${bueongi.facility.refresh-cron}", zone = "Asia/Seoul")
    fun onSchedule() {
        if (!refreshService.isEnabled) {
            return
        }
        log.info("안심시설 라이브 갱신 시작(주기 스케줄)")
        refreshService.refreshAll()
    }
}
