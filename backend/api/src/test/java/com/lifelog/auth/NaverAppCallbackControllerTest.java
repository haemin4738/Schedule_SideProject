package com.lifelog.auth;

import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NaverAppCallbackController.class,
        properties = "oauth.naver.app-callback-target=lifelog://oauth/naver")
@Import(SecurityConfig.class)
class NaverAppCallbackControllerTest {

    private static final String PATH = "/api/v1/auth/social/naver/app-callback";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void callback_whenCodeAndState_redirectsToFixedTargetWithSecurityHeaders() throws Exception {
        mockMvc.perform(get(PATH).param("code", "abc123").param("state", "xyz"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "lifelog://oauth/naver?code=abc123&state=xyz"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void callback_whenValuesContainReservedCharacters_encodesThem() throws Exception {
        String code = "a&b=c+d#e f/g?h%i";
        String state = "s&t=u";

        MvcResult result = mockMvc.perform(get(PATH).param("code", code).param("state", state))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).isEqualTo(
                "lifelog://oauth/naver?code=a%26b%3Dc%2Bd%23e%20f%2Fg%3Fh%25i&state=s%26t%3Du");
        // 인코딩된 값이 딥링크 쪽에서 원문으로 복원되는지 (쿼리 파라미터가 늘어나거나 fragment 로 잘리지 않음)
        UriComponents parsed = UriComponentsBuilder.fromUriString(location).build();
        assertThat(parsed.getFragment()).isNull();
        assertThat(parsed.getQueryParams()).containsOnlyKeys("code", "state");
        assertThat(URLDecoder.decode(parsed.getQueryParams().getFirst("code").replace("+", "%2B"),
                StandardCharsets.UTF_8)).isEqualTo(code);
    }

    @Test
    void callback_whenError_forwardsOnlyError() throws Exception {
        mockMvc.perform(get(PATH).param("error", "access_denied").param("state", "xyz"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "lifelog://oauth/naver?state=xyz&error=access_denied"));
    }

    @Test
    void callback_whenNoParams_redirectsToBareTarget() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "lifelog://oauth/naver"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void callback_whenExtraOrRedirectParams_ignoresThemAndKeepsFixedTarget() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("code", "c1")
                        .param("redirect_uri", "https://evil.example.com")
                        .param("target", "https://evil.example.com")
                        .param("error_description", "desc"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "lifelog://oauth/naver?code=c1"));
    }

    @Test
    void callback_whenUnauthenticated_isPermitted() throws Exception {
        // 인증 헤더 없이 접근 가능 (/api/v1/auth/** permitAll)
        mockMvc.perform(get(PATH).param("code", "c"))
                .andExpect(status().isFound());
    }
}
