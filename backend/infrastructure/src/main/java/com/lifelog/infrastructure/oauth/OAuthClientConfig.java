package com.lifelog.infrastructure.oauth;

import com.lifelog.infrastructure.config.HttpClientFactories;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(OAuthProviderProperties.class)
public class OAuthClientConfig {

    static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    /** 소셜 제공자 전용 RestClient — connect/read 타임아웃 적용 */
    @Bean
    public RestClient socialRestClient(RestClient.Builder builder, OAuthProviderProperties properties) {
        return builder.clone()
                .requestFactory(requestFactory(properties.http()))
                .build();
    }

    /**
     * 구글 id_token 서명(JWKS) + exp/nbf 검증용 디코더. iss/aud/nonce 는 {@link GoogleIdentityClient} 가 검증한다.
     * JWK Set 은 첫 검증 시점에 조회하므로 네트워크 없이도 기동된다.
     */
    @Bean
    public JwtDecoder googleIdTokenDecoder(OAuthProviderProperties properties) {
        return NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI)
                .restOperations(new RestTemplate(requestFactory(properties.http())))
                .build();
    }

    private static JdkClientHttpRequestFactory requestFactory(OAuthProviderProperties.Http http) {
        return HttpClientFactories.jdk(http.connectTimeout(), http.readTimeout());
    }
}
