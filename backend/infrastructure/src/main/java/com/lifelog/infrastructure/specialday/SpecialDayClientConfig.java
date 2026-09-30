package com.lifelog.infrastructure.specialday;

import com.lifelog.infrastructure.config.HttpClientFactories;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SpecialDayProperties.class)
public class SpecialDayClientConfig {

    /** 특일 API 전용 RestClient — connect/read 타임아웃 적용, 리다이렉트 미추적 */
    @Bean
    public RestClient specialDayRestClient(RestClient.Builder builder, SpecialDayProperties properties) {
        return builder.clone()
                .requestFactory(HttpClientFactories.jdk(properties.http().connectTimeout(), properties.http().readTimeout()))
                .build();
    }
}
