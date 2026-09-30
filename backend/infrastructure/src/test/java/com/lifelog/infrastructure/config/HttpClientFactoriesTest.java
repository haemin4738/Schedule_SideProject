package com.lifelog.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientFactoriesTest {

    @Test
    void jdk_whenTimeoutsGiven_appliesTimeoutsAndNeverFollowsRedirects() {
        JdkClientHttpRequestFactory factory = HttpClientFactories.jdk(Duration.ofSeconds(2), Duration.ofSeconds(7));

        HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(factory, "httpClient");
        assertThat(httpClient).isNotNull();
        assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(2));
        assertThat(httpClient.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(Duration.ofSeconds(7));
    }
}
