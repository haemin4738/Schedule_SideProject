package com.lifelog.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yml 의 Redis timeout 이 실제 Lettuce 연결 설정에 반영되는지 확인한다 (기본값 60초로 돌아가는 회귀 방지).
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisTimeoutConfigIntegrationTest {

    @Autowired
    private LettuceConnectionFactory connectionFactory;

    @Test
    void lettuceClient_whenConfigured_usesShortCommandAndConnectTimeouts() {
        var clientConfiguration = connectionFactory.getClientConfiguration();

        assertThat(clientConfiguration.getCommandTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(clientConfiguration.getClientOptions())
                .hasValueSatisfying(options ->
                        assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofSeconds(2)));
    }
}
