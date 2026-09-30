package com.lifelog.infrastructure.specialday;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecialDayPropertiesTest {

    @Test
    void constructor_whenAllNull_appliesDefaults() {
        SpecialDayProperties props = new SpecialDayProperties(null, null, null);

        assertThat(props.serviceKey()).isEmpty();
        assertThat(props.hasServiceKey()).isFalse();
        assertThat(props.baseUrl()).isEqualTo("https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService");
        assertThat(props.http().connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(props.http().readTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void constructor_whenBlankValues_appliesDefaultsAndTrimsKey() {
        SpecialDayProperties props = new SpecialDayProperties("  key+/= ", " ", null);

        assertThat(props.serviceKey()).isEqualTo("key+/=");
        assertThat(props.hasServiceKey()).isTrue();
        assertThat(props.baseUrl()).isEqualTo(SpecialDayProperties.DEFAULT_BASE_URL);
    }

    @Test
    void bind_whenYamlStyleProperties_bindsWithUnsetEnvAsEmptyAndIgnoresSyncKeys() {
        Map<String, String> source = Map.of(
                "special-day.service-key", "",
                "special-day.base-url", "http://localhost:9999/svc",
                "special-day.http.read-timeout", "2s",
                "special-day.sync.enabled", "false");

        SpecialDayProperties props = new Binder(new MapConfigurationPropertySource(source))
                .bind("special-day", SpecialDayProperties.class).get();

        assertThat(props.hasServiceKey()).isFalse();
        assertThat(props.baseUrl()).isEqualTo("http://localhost:9999/svc");
        assertThat(props.http().connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(props.http().readTimeout()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void toString_whenKeySet_masksKey() {
        String text = new SpecialDayProperties("ab+c/d==secret", null, null).toString();

        assertThat(text).contains("serviceKey=***").doesNotContain("ab+c", "secret");
        assertThat(new SpecialDayProperties(null, null, null).toString()).contains("serviceKey=(unset)");
    }

    @Test
    void specialDayClientConfig_whenBuildingBean_createsClientWithoutNetwork() {
        RestClient restClient = new SpecialDayClientConfig()
                .specialDayRestClient(RestClient.builder(), new SpecialDayProperties(null, null, null));

        assertThat(restClient).isNotNull();
    }
}
