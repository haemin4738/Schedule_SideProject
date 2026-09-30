package com.lifelog.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 JwtTokenProvider로 필터의 토큰 타입 검증 경로를 확인한다.
 */
class JwtAuthenticationFilterTest {

    private static final String SSE_PATH = "/api/v1/sse/events";
    private static final Long USER_ID = 7L;

    private final JwtTokenProvider provider =
            JwtTokenProviderTest.create(JwtTokenProviderTest.SECRET, 3_600_000L, 3_600_000L);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain doFilter(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }

    @Test
    void doFilterInternal_withBearerAccessToken_setsAuthentication() throws Exception {
        MockHttpServletRequest request = request("/api/v1/events");
        request.addHeader("Authorization", "Bearer " + provider.createAccessToken(USER_ID));

        MockFilterChain chain = doFilter(request);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(USER_ID);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilterInternal_withBearerRefreshToken_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = request("/api/v1/events");
        request.addHeader("Authorization", "Bearer " + JwtTokenProviderTest.refreshToken(provider, USER_ID));

        MockFilterChain chain = doFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilterInternal_withBearerInvalidToken_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = request("/api/v1/events");
        request.addHeader("Authorization", "Bearer forged.token.value");

        MockFilterChain chain = doFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilterInternal_withoutToken_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = request("/api/v1/events");

        MockFilterChain chain = doFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilterInternal_whenSseQueryAccessToken_setsAuthentication() throws Exception {
        MockHttpServletRequest request = request(SSE_PATH);
        request.setParameter("token", provider.createAccessToken(USER_ID));

        MockFilterChain chain = doFilter(request);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(USER_ID);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilterInternal_whenSseQueryRefreshToken_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = request(SSE_PATH);
        request.setParameter("token", JwtTokenProviderTest.refreshToken(provider, USER_ID));

        MockFilterChain chain = doFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilterInternal_whenQueryTokenOnNonSsePath_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = request("/api/v1/events");
        request.setParameter("token", provider.createAccessToken(USER_ID));

        MockFilterChain chain = doFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
