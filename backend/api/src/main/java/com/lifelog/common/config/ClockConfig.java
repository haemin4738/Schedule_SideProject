package com.lifelog.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 시간 의존 로직(토큰 만료, 회전 유예)을 테스트에서 고정할 수 있도록 Clock 을 빈으로 주입한다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
