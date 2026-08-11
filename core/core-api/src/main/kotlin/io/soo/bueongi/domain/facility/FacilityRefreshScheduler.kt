package io.soo.bueongi.domain.facility

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class FacilityRefreshScheduler(
    private val refreshService: FacilityRefreshService,
    private val taskExecutor: TaskExecutor,
    @Value("\${bueongi.facility.refresh-on-startup:false}") private val refreshOnStartup: Boolean,
) {
    private val log: Logger = LoggerFactory.getLogger(FacilityRefreshScheduler::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (!refreshOnStartup) {
            log.info(
                "안심시설 기동 갱신 비활성(bueongi.facility.refresh-on-startup=false)" +
                    " — 파일 적재분으로 동작하고 주기 스케줄로만 갱신한다",
            )
            return
        }
        if (!refreshService.isEnabled) {
            log.info(
                "안심시설 라이브 갱신 비활성(DATA_GO_KR_SERVICE_KEY·SAFEMAP_SERVICE_KEY·SAFETYDATA_SERVICE_KEY 미설정)" +
                    " — 파일 적재분으로 동작",
            )
            return
        }
        taskExecutor.execute {
            log.info("안심시설 라이브 갱신 시작(기동 직후 1회)")
            refreshService.refreshAll()
        }
    }

    @Scheduled(cron = "\${bueongi.facility.refresh-cron}", zone = "Asia/Seoul")
    fun onSchedule() {
        if (!refreshService.isEnabled) {
            return
        }
        log.info("안심시설 라이브 갱신 시작(주기 스케줄)")
        refreshService.refreshAll()
    }
}
