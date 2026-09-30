package com.lifelog.infrastructure.config;

import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/** 외부 API 호출용 요청 팩토리 공통 생성 */
public final class HttpClientFactories {

    private HttpClientFactories() {
    }

    /**
     * JDK HttpClient 기반 요청 팩토리. 리다이렉트는 따라가지 않는다(인증 정보가 다른 호스트로 전달되는 것 방지).
     *
     * @param connectTimeout 연결 타임아웃
     * @param readTimeout    응답 읽기 타임아웃
     */
    public static JdkClientHttpRequestFactory jdk(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
