package io.soo.bueongi.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** @Scheduled 활성화 — 현재 사용처: 안심시설 라이브 갱신(FacilityRefreshScheduler). */
@Configuration
@EnableScheduling
class SchedulingConfig
