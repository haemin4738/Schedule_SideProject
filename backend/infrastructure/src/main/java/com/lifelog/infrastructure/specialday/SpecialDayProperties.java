package com.lifelog.infrastructure.specialday;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 특일(공휴일·기념일·24절기) 외부 API 설정 ({@code special-day.*}).
 * serviceKey 가 비어 있어도 기동은 성공하고, 외부 조회만 생략된다.
 * 정기 동기화 설정({@code special-day.sync.enabled/cron})은 api 모듈의 SpecialDaySyncScheduler 가 직접 읽는다.
 *
 * @param serviceKey 공공데이터포털 일반 인증키(마이페이지에 보이는 값 그대로 — Encoding/Decoding 형태 모두 허용).
 *                   로그·예외 메시지에 넣지 않는다({@link #toString()} 에서도 마스킹).
 * @param baseUrl    SpcdeInfoService 기본 URL
 */
@ConfigurationProperties("special-day")
public record SpecialDayProperties(String serviceKey, String baseUrl, Http http) {

    public static final String DEFAULT_BASE_URL = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService";

    public SpecialDayProperties {
        serviceKey = serviceKey != null ? serviceKey.trim() : "";
        baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : DEFAULT_BASE_URL;
        http = http != null ? http : new Http(null, null);
    }

    public boolean hasServiceKey() {
        return !serviceKey.isEmpty();
    }

    /** 인증키 값 대신 설정 여부만 노출한다 (설정 덤프·디버그 로그 대비) */
    @Override
    public String toString() {
        return "SpecialDayProperties[serviceKey=" + (hasServiceKey() ? "***" : "(unset)")
                + ", baseUrl=" + baseUrl + ", http=" + http + "]";
    }

    /** 외부 API 호출 타임아웃 */
    public record Http(Duration connectTimeout, Duration readTimeout) {
        public Http {
            connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(3);
            readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(5);
        }
    }
}
