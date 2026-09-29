package com.lifelog.infrastructure.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthProviderPropertiesTest {

    @Test
    void constructor_whenAllNull_appliesDefaults() {
        OAuthProviderProperties props = new OAuthProviderProperties(null, null, null, null);

        assertThat(props.http().connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(props.http().readTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.kakao().allowedRedirectUris()).isEmpty();
        assertThat(props.naver().allowedRedirectUris()).isEmpty();
        assertThat(props.google().allowedAudiences()).isEmpty();
        assertThat(props.google().allowedRedirectUris()).isEmpty();
    }

    @Test
    void google_whenListsContainBlankOrNull_filtersAndTrims() {
        OAuthProviderProperties.Google google = new OAuthProviderProperties.Google("id", "secret",
                Arrays.asList("", " web ", null, "  "), Arrays.asList(null, "http://cb"));

        assertThat(google.allowedAudiences()).containsExactly("web");
        assertThat(google.allowedRedirectUris()).containsExactly("http://cb");
    }

    @Test
    void bind_whenYamlStyleProperties_bindsWithUnsetEnvIgnored() {
        Map<String, String> source = Map.of(
                "oauth.http.connect-timeout", "1s",
                "oauth.kakao.client-id", "rest",
                "oauth.kakao.allowed-redirect-uris[0]", "http://localhost:5173/oauth/callback/kakao",
                "oauth.google.allowed-audiences[0]", "web-id",
                "oauth.google.allowed-audiences[1]", "",
                "oauth.naver.app-callback-target", "lifelog://oauth/naver");

        OAuthProviderProperties props = new Binder(new MapConfigurationPropertySource(source))
                .bind("oauth", OAuthProviderProperties.class).get();

        assertThat(props.http().connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(props.http().readTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.kakao().clientId()).isEqualTo("rest");
        assertThat(props.kakao().allowedRedirectUris()).containsExactly("http://localhost:5173/oauth/callback/kakao");
        assertThat(props.google().allowedAudiences()).containsExactly("web-id");
        assertThat(props.naver().appCallbackTarget()).isEqualTo("lifelog://oauth/naver");
    }

    @Test
    void oauthClientConfig_whenBuildingBeans_createsClientAndDecoderWithoutNetwork() {
        OAuthClientConfig config = new OAuthClientConfig();
        OAuthProviderProperties props = new OAuthProviderProperties(null, null, null, null);

        RestClient restClient = config.socialRestClient(RestClient.builder(), props);
        JwtDecoder decoder = config.googleIdTokenDecoder(props);

        assertThat(restClient).isNotNull();
        assertThat(decoder).isNotNull();
        assertThat(List.of(OAuthClientConfig.GOOGLE_JWK_SET_URI)).containsExactly("https://www.googleapis.com/oauth2/v3/certs");
    }
}
