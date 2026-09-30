package com.lifelog.auth;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * api 모듈 첫 전체 컨텍스트 통합 테스트 — 실제 MySQL(lifelog_test) + 실제 Redis(REDIS_HOST).
 * Clock 빈을 테스트용 가변 Clock 으로 교체해 유예(30초)·idle(15일)·절대(30일) 수명 경계를 재현한다.
 * JWT 만료와 회전 유예 판정은 모두 이 Clock 기준이며, Redis 의 실제 TTL 은 테스트 시간 동안 만료되지 않는다.
 *
 * <p>테스트마다 고유 이메일로 가입하고, 종료 시 해당 사용자 행과 Redis 키(auth:refresh:{u:id}:*)를 삭제한다.
 * api 모듈은 spring-data-redis/spring-jdbc 에 컴파일 의존이 없으므로(빌드 파일 변경 금지)
 * 정리는 DataSource(JDK) 와 StringRedisTemplate 빈 리플렉션으로 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSessionIntegrationTest {

    private static final String BASE = "/api/v1/auth";
    private static final String PASSWORD = "Passw0rd!x";
    private static final Duration GRACE = Duration.ofSeconds(30);
    private static final String SESSION_REVOKED_MESSAGE = "보안을 위해 모든 기기에서 로그아웃되었습니다. 다시 로그인해 주세요.";

    /** 테스트에서 시각을 앞으로 돌릴 수 있는 Clock */
    static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationContext context;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final List<String> createdEmails = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    /** 가장 최근 signup() 으로 만든 사용자 이메일 */
    private String lastEmail;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    @AfterEach
    void cleanUp() throws Exception {
        Object redis = context.getBean("stringRedisTemplate");
        Method keys = redis.getClass().getMethod("keys", Object.class);
        Method delete = redis.getClass().getMethod("delete", Collection.class);
        for (Long userId : createdUserIds) {
            Collection<?> found = (Collection<?>) keys.invoke(redis, "auth:refresh:{u:" + userId + "}:*");
            if (found != null && !found.isEmpty()) {
                delete.invoke(redis, found);
            }
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE email = ?")) {
            for (String email : createdEmails) {
                statement.setString(1, email);
                statement.executeUpdate();
            }
        }
    }

    // ---------- helpers ----------

    private Long signup() throws Exception {
        String email = "session-it-" + UUID.randomUUID() + "@test.com";
        String body = mockMvc.perform(post(BASE + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"세션\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        createdEmails.add(email);
        Long userId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        createdUserIds.add(userId);
        lastEmail = email;
        return userId;
    }

    private ResultActions loginRequest(String email) throws Exception {
        return mockMvc.perform(post(BASE + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    /** 로그인 후 refresh 토큰 반환 */
    private String login(String email) throws Exception {
        String body = loginRequest(email)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.data.refreshToken");
    }

    private ResultActions refreshRequest(String refreshToken) throws Exception {
        return mockMvc.perform(post(BASE + "/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    /** refresh 성공(200) 후 새 refresh 토큰 반환 */
    private String refresh(String refreshToken) throws Exception {
        String body = refreshRequest(refreshToken)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.data.refreshToken");
    }

    private ResultActions logoutRequest(String refreshToken) throws Exception {
        return mockMvc.perform(post(BASE + "/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private void expectError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.code").value(code));
    }

    // ---------- 재사용 탐지 / 유예 ----------

    @Test
    void refresh_whenOldTokenReusedAfterGrace_revokesAllSessionsOfUser() throws Exception {
        signup();
        String deviceA0 = login(lastEmail);
        String deviceB = login(lastEmail);

        String deviceA1 = refresh(deviceA0);
        clock.advance(GRACE.plusSeconds(1));

        // 유예 경과 후 이전 토큰 재사용 → 재사용 탐지, 사용자 전체 세션 폐기
        expectError(refreshRequest(deviceA0), 401, "SESSION_REVOKED");
        refreshRequest(deviceA0).andExpect(jsonPath("$.error").value(SESSION_REVOKED_MESSAGE));
        // 다른 기기(B)와 A 의 최신 토큰도 모두 폐기됨
        expectError(refreshRequest(deviceB), 401, "SESSION_REVOKED");
        expectError(refreshRequest(deviceA1), 401, "SESSION_REVOKED");
    }

    @Test
    void refresh_whenOldTokenReusedWithinGrace_returns409AndKeepsSession() throws Exception {
        signup();
        String deviceA0 = login(lastEmail);
        String deviceB = login(lastEmail);

        String deviceA1 = refresh(deviceA0);
        clock.advance(GRACE.minusSeconds(1));

        expectError(refreshRequest(deviceA0), 409, "REFRESH_IN_PROGRESS");

        // 세션 유지: 최신 토큰으로 계속 회전되고, 다른 기기도 영향 없음
        String deviceA2 = refresh(deviceA1);
        assertThat(deviceA2).isNotEqualTo(deviceA1);
        refresh(deviceB);
    }

    // ---------- 로그아웃 ----------

    @Test
    void logout_thenRefresh_returns401InvalidAndOtherDeviceUnaffected() throws Exception {
        signup();
        String deviceA = login(lastEmail);
        String deviceB = login(lastEmail);

        logoutRequest(deviceA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").doesNotExist());

        expectError(refreshRequest(deviceA), 401, "INVALID_REFRESH_TOKEN");
        refresh(deviceB);
    }

    @Test
    void logout_whenCalledTwice_returns200Both() throws Exception {
        signup();
        String token = login(lastEmail);

        logoutRequest(token).andExpect(status().isOk());
        logoutRequest(token).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void logout_whenNoAuthorizationHeader_returns200() throws Exception {
        signup();
        String token = login(lastEmail);

        // 인증 헤더 없이 호출(permitAll). 잘못된 토큰 문자열도 멱등 200
        logoutRequest(token).andExpect(status().isOk());
        logoutRequest("not-a-jwt").andExpect(status().isOk());
    }

    @Test
    void logout_whenRefreshTokenBlank_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---------- 수명 ----------

    @Test
    void refresh_whenIdleExceeds15Days_returns401Invalid() throws Exception {
        signup();
        String token = login(lastEmail);

        clock.advance(Duration.ofDays(15).plusSeconds(1));

        expectError(refreshRequest(token), 401, "INVALID_REFRESH_TOKEN");
    }

    @Test
    void refresh_whenJustBeforeIdle15Days_succeeds() throws Exception {
        signup();
        String token = login(lastEmail);

        clock.advance(Duration.ofDays(15).minusSeconds(1));

        refresh(token);
    }

    @Test
    void refresh_whenRotatedWithinIdleButPastAbsolute30Days_returns401Invalid() throws Exception {
        signup();
        String token = login(lastEmail);

        // 15일 이내 간격으로 회전해도 절대 수명(최초 로그인 +30일)은 연장되지 않는다
        clock.advance(Duration.ofDays(14));
        token = refresh(token);
        clock.advance(Duration.ofDays(14));
        token = refresh(token);
        clock.advance(Duration.ofDays(2).minusSeconds(1)); // 로그인 +30일 -1초 → 아직 유효
        token = refresh(token);

        clock.advance(Duration.ofSeconds(2)); // 로그인 +30일 +1초

        expectError(refreshRequest(token), 401, "INVALID_REFRESH_TOKEN");
    }

    // ---------- 레거시 ----------

    @Test
    void refresh_whenLegacyTokenWithoutJtiAndSid_returns401Invalid() throws Exception {
        Long userId = signup();
        Instant now = clock.instant();
        String legacy = Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofDays(7))))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        expectError(refreshRequest(legacy), 401, "INVALID_REFRESH_TOKEN");
        logoutRequest(legacy).andExpect(status().isOk());
    }

    // ---------- envelope ----------

    @Test
    void login_whenSuccess_returnsEnvelopeWithoutCode() throws Exception {
        signup();

        loginRequest(lastEmail)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void refresh_whenSuccess_returnsEnvelopeWithoutCodeAndNewTokens() throws Exception {
        signup();
        String token = login(lastEmail);

        String body = refreshRequest(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat((String) JsonPath.read(body, "$.data.refreshToken")).isNotEqualTo(token);
        // error 는 null 이어도 키가 유지된다(기존 계약)
        assertThat(body).contains("\"error\":null");
    }

    @Test
    void refresh_whenNewAccessTokenUsed_returns200OnProtectedApi() throws Exception {
        signup();
        String token = login(lastEmail);
        String body = refreshRequest(token).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String access = JsonPath.read(body, "$.data.accessToken");

        mockMvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void refresh_whenTokenGarbage_returns401InvalidWithEnvelope() throws Exception {
        refreshRequest("garbage")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}
