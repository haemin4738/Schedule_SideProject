package com.lifelog.infrastructure.oauth;

import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialAuthException.Reason;
import com.lifelog.domain.user.social.SocialProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** 제공자 HTTP 호출 공통 처리 — 오류 매핑과 JSON 필드 추출 */
@Slf4j
final class ProviderCalls {

    /** 제공자 에러 코드로 기록해도 안전한 형태 (KOE010, -401, invalid_grant 등) */
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("-?[A-Za-z0-9_]{1,40}");

    private ProviderCalls() {
    }

    /**
     * 4xx → INVALID_CREDENTIAL, 5xx·타임아웃·I/O·응답 파싱 실패·빈 응답 → PROVIDER_UNAVAILABLE.
     * 예외 메시지에 응답 본문(토큰 등)이 섞이지 않도록 원인 예외 메시지를 옮기지 않는다.
     */
    static JsonNode call(SocialProvider provider, String step, Supplier<JsonNode> request) {
        JsonNode body;
        try {
            body = request.get();
        } catch (HttpClientErrorException e) {
            // 설정 오류(KOE010 등)·한도 초과를 사용자 인증 실패와 구분할 수 있도록 에러 코드만 기록
            log.warn("소셜 제공자 요청 거부: provider={}, step={}, status={}, errorCode={}",
                    provider, step, e.getStatusCode().value(), errorCode(e));
            throw new SocialAuthException(Reason.INVALID_CREDENTIAL,
                    provider + " " + step + " rejected: HTTP " + e.getStatusCode().value(), e);
        } catch (HttpServerErrorException e) {
            throw unavailable(provider, step + " failed: HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientResponseException e) {
            // 1xx/3xx 등 예상 밖 상태
            throw unavailable(provider, step + " unexpected: HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            // ResourceAccessException(타임아웃·연결 실패), 응답 변환 실패 등
            throw unavailable(provider, step + " failed: " + e.getClass().getSimpleName(), e);
        }
        if (body == null || !body.isObject()) {
            throw unavailable(provider, step + " returned empty body", null);
        }
        return body;
    }

    static SocialAuthException invalidRequest(String message) {
        return new SocialAuthException(Reason.INVALID_REQUEST, message);
    }

    static SocialAuthException invalidCredential(String message) {
        return new SocialAuthException(Reason.INVALID_CREDENTIAL, message);
    }

    static SocialAuthException notConfigured(SocialProvider provider) {
        return new SocialAuthException(Reason.SERVICE_UNAVAILABLE, provider + " login is not configured");
    }

    static SocialAuthException unavailable(SocialProvider provider, String message, Throwable cause) {
        return new SocialAuthException(Reason.PROVIDER_UNAVAILABLE, provider + " " + message, cause);
    }

    /**
     * 허용 목록과 정확히 일치하지 않는 redirectUri 는 거부 (정규화·prefix 매칭 없음).
     * 허용 목록 자체가 비어 있으면 서버 설정 누락이므로 503
     */
    static void requireAllowedRedirectUri(SocialProvider provider, String redirectUri, List<String> allowed) {
        if (allowed.isEmpty()) {
            throw notConfigured(provider);
        }
        if (redirectUri == null || !allowed.contains(redirectUri)) {
            throw invalidRequest(provider + " redirectUri is not allowed");
        }
    }

    /** 응답 본문에서 제공자 에러 코드(error_code / code / error)만 추출. 형식이 안전하지 않으면 null */
    private static String errorCode(RestClientResponseException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            if (body == null) {
                return null;
            }
            for (String field : List.of("error_code", "code", "error")) {
                String value = text(body.get(field));
                if (value != null && SAFE_ERROR_CODE.matcher(value).matches()) {
                    return value;
                }
            }
        } catch (RuntimeException ignored) {
            // 본문이 JSON 이 아니면 코드 없이 기록
        }
        return null;
    }

    static boolean hasText(String value) {
        return OAuthProviderProperties.hasText(value);
    }

    static void requireText(String value, String field) {
        if (!hasText(value)) {
            throw invalidRequest(field + " is required");
        }
    }

    /** 문자열/숫자 노드만 문자열로 반환, 그 외(누락·null·객체)는 null */
    static String text(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isString()) {
            String value = node.stringValue();
            return value.isBlank() ? null : value;
        }
        if (node.isNumber()) {
            return node.asString();
        }
        return null;
    }

    /** boolean true 일 때만 true (누락·문자열 등은 false) */
    static boolean isTrue(JsonNode node) {
        return node != null && node.isBoolean() && node.booleanValue();
    }
}
