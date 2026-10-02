package com.lifelog.auth;

import com.jayway.jsonpath.JsonPath;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialIdentityVerifier;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 네이버 앱 로그인 전 구간(start → 네이버 콜백 → complete) — 실제 Redis·MySQL, 네이버 HTTP 호출만 대체.
 */
@SpringBootTest(properties = "oauth.naver.client-id=test-naver-client")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NaverAppLoginIntegrationTest {

    private static final String BASE = "/api/v1/auth/social/naver";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private SocialIdentityVerifier verifier;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() throws Exception {
        Set<String> keys = redis.keys("auth:social-app:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement accounts = connection.prepareStatement(
                     "DELETE sa FROM social_accounts sa JOIN users u ON sa.user_id = u.id WHERE u.email = ?");
             PreparedStatement users = connection.prepareStatement("DELETE FROM users WHERE email = ?")) {
            for (String email : createdEmails) {
                accounts.setString(1, email);
                accounts.executeUpdate();
                users.setString(1, email);
                users.executeUpdate();
            }
        }
    }

    private static String newVerifier() {
        return (UUID.randomUUID() + "-" + UUID.randomUUID()).replace("-", "");
    }

    private static String challengeOf(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /** start 후 authorize URL 의 state 반환 */
    private String start(String verifier) throws Exception {
        String body = mockMvc.perform(post(BASE + "/app/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codeChallenge\":\"" + challengeOf(verifier) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String authorizeUrl = JsonPath.read(body, "$.data.authorizeUrl");
        return UriComponentsBuilder.fromUriString(authorizeUrl).build().getQueryParams().getFirst("state");
    }

    /** 네이버가 브라우저를 app-callback 으로 보낸 것처럼 호출하고 Location 을 반환 */
    private UriComponents callback(String code, String state) throws Exception {
        String location = mockMvc.perform(get(BASE + "/app-callback").param("code", code).param("state", state))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        return UriComponentsBuilder.fromUriString(location).build();
    }

    private ResultActions complete(String ticket, String verifier) throws Exception {
        return mockMvc.perform(post(BASE + "/app/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticket\":\"" + ticket + "\",\"codeVerifier\":\"" + verifier + "\"}"));
    }

    private SocialUserInfo naverUser() {
        String email = "naver-app-it-" + UUID.randomUUID() + "@test.com";
        createdEmails.add(email);
        return new SocialUserInfo(SocialProvider.NAVER, "naver-" + UUID.randomUUID(), email, true, "네이버");
    }

    @Test
    void fullFlow_whenNewNaverUser_signsUpWithTicketAndVerifierOnce() throws Exception {
        String verifierValue = newVerifier();
        String state = start(verifierValue);
        when(verifier.verify(SocialProvider.NAVER, new SocialCredential.ServerCallbackCode("naver-code", state)))
                .thenReturn(naverUser());

        UriComponents location = callback("naver-code", state);

        assertThat(location.toUriString()).startsWith("lifelog://oauth/naver?ticket=").doesNotContain("naver-code");
        String ticket = location.getQueryParams().getFirst("ticket");
        complete(ticket, verifierValue)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOGGED_IN"))
                .andExpect(jsonPath("$.data.newUser").value(true))
                .andExpect(jsonPath("$.data.token.refreshToken").isNotEmpty());
        // ticket 은 1회용
        complete(ticket, verifierValue)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_APP_TICKET_INVALID"));
    }

    @Test
    void complete_whenInterceptedTicketUsedWithOtherVerifier_rejectsAndBurnsTicket() throws Exception {
        String verifierValue = newVerifier();
        String state = start(verifierValue);
        when(verifier.verify(eq(SocialProvider.NAVER), any())).thenReturn(naverUser());
        String ticket = callback("naver-code", state).getQueryParams().getFirst("ticket");

        // 콜백을 가로챈 다른 앱은 verifier 를 모른다
        complete(ticket, newVerifier())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_APP_TICKET_INVALID"));
        // 시도한 ticket 은 소비돼 원래 앱도 다시 시작해야 한다
        complete(ticket, verifierValue).andExpect(status().isUnauthorized());
    }

    @Test
    void callback_whenStateReusedOrUnknown_redirectsInvalidStateWithoutExchange() throws Exception {
        String state = start(newVerifier());
        when(verifier.verify(eq(SocialProvider.NAVER), any())).thenReturn(naverUser());
        callback("naver-code", state);

        assertThat(callback("naver-code-2", state).getQueryParams().getFirst("error")).isEqualTo("INVALID_STATE");
        assertThat(callback("naver-code-3", "forged-state").getQueryParams().getFirst("error")).isEqualTo("INVALID_STATE");
        verify(verifier, never()).verify(SocialProvider.NAVER, new SocialCredential.ServerCallbackCode("naver-code-2", state));
    }
}
