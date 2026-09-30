package com.lifelog.infrastructure.specialday;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 특일(공휴일·기념일·24절기) 외부 API 설정 ({@code special-day.*}).
 * serviceKey 가 비어 있어도 기동은 성공하고, 외부 조회만 생략된다.
 *
 * @param serviceKey 공공데이터포털 일반 인증키(마이페이지에 보이는 값 그대로 — Encoding/Decoding 형태 모두 허용).
 *                   로그·예외 메시지에 넣지 않는다.
 * @param baseUrl    SpcdeInfoService 기본 URL
 */
@ConfigurationProperties("special-day")
public record SpecialDayProperties(String serviceKey, String baseUrl, Http http, Sync sync) {

    public static final String DEFAULT_BASE_URL = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService";

    public SpecialDayProperties {
        serviceKey = serviceKey != null ? serviceKey.trim() : "";
        baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : DEFAULT_BASE_URL;
        http = http != null ? http : new Http(null, null);
        sync = sync != null ? sync : new Sync(null, null);
    }

    public boolean hasServiceKey() {
        return !serviceKey.isEmpty();
    }

    /** 외부 API 호출 타임아웃 */
    public record Http(Duration connectTimeout, Duration readTimeout) {
        public Http {
            connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(3);
            readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(5);
        }
    }

    /**
     * 정기 동기화 설정. 실제 스케줄 등록은 api 모듈이 같은 키
     * ({@code special-day.sync.enabled}, {@code special-day.sync.cron})를 직접 읽는다.
     */
    public record Sync(Boolean enabled, String cron) {
        public static final String DEFAULT_CRON = "0 0 4 * * *";

        public Sync {
            enabled = enabled != null ? enabled : Boolean.TRUE;
            cron = cron != null && !cron.isBlank() ? cron : DEFAULT_CRON;
        }
    }
}
