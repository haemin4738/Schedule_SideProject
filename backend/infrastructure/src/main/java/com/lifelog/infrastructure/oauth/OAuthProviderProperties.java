package com.lifelog.infrastructure.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 소셜 로그인 제공자 설정 ({@code oauth.*}).
 * 값이 비어 있어도 기동은 성공하고, 해당 제공자 호출 시점에 SERVICE_UNAVAILABLE 로 처리한다.
 * 목록 값의 빈 문자열(미설정 환경변수)은 무시한다.
 */
@ConfigurationProperties("oauth")
public record OAuthProviderProperties(Http http, Kakao kakao, Naver naver, Google google) {

    public OAuthProviderProperties {
        http = http != null ? http : new Http(null, null);
        kakao = kakao != null ? kakao : new Kakao(null, null, null, null);
        naver = naver != null ? naver : new Naver(null, null, null, null, null);
        google = google != null ? google : new Google(null, null, null, null);
    }

    /** 제공자 API 호출 타임아웃 */
    public record Http(Duration connectTimeout, Duration readTimeout) {
        public Http {
            connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(3);
            readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(5);
        }
    }

    /** clientId = REST API 키, appId = access token 발급 앱 대조용 */
    public record Kakao(String clientId, String clientSecret, String appId, List<String> allowedRedirectUris) {
        public Kakao {
            allowedRedirectUris = nonBlank(allowedRedirectUris);
        }
    }

    /**
     * allowedRedirectUris = 웹 로그인(/social/naver/login) 전용.
     * appRedirectUri = 앱 로그인의 네이버 redirect_uri(서버 app-callback 절대 URL),
     * appCallbackTarget = app-callback 이 ticket 을 붙여 보내는 앱 고정 대상 (둘 다 api 모듈 앱 로그인에서 사용)
     */
    public record Naver(String clientId, String clientSecret, List<String> allowedRedirectUris,
                        String appRedirectUri, String appCallbackTarget) {
        public Naver {
            allowedRedirectUris = nonBlank(allowedRedirectUris);
        }
    }

    /** clientId/clientSecret = 웹 클라이언트 (code 교환용), allowedAudiences = id_token aud 허용 목록 (웹 + iOS) */
    public record Google(String clientId, String clientSecret, List<String> allowedAudiences, List<String> allowedRedirectUris) {
        public Google {
            allowedAudiences = nonBlank(allowedAudiences);
            allowedRedirectUris = nonBlank(allowedRedirectUris);
        }
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static List<String> nonBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }
}
