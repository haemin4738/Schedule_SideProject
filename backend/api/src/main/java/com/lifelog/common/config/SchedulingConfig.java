package com.lifelog.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 작업 활성화. 개별 작업은 각자 조건(프로퍼티)으로 빈 등록 여부를 정한다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
