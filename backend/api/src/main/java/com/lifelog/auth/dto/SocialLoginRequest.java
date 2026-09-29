package com.lifelog.auth.dto;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.user.social.SocialCredential;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 소셜 로그인 요청. grantType 별 필수값:
 * AUTHORIZATION_CODE → code, redirectUri (codeVerifier/nonce/state 선택, 네이버는 state 필수),
 * ACCESS_TOKEN → accessToken, ID_TOKEN → idToken (nonce 선택)
 */
public record SocialLoginRequest(
        @NotNull(message = "grantType은 필수입니다.")
        SocialGrantType grantType,

        @Size(max = 2048, message = "code가 너무 깁니다.")
        String code,

        @Size(max = 2048, message = "redirectUri가 너무 깁니다.")
        String redirectUri,

        @Size(max = 128, message = "codeVerifier가 너무 깁니다.")
        String codeVerifier,

        @Size(max = 512, message = "nonce가 너무 깁니다.")
        String nonce,

        @Size(max = 512, message = "state가 너무 깁니다.")
        String state,

        @Size(max = 8192, message = "accessToken이 너무 깁니다.")
        String accessToken,

        @Size(max = 8192, message = "idToken이 너무 깁니다.")
        String idToken
) {

    /** grantType 별 필수값을 검증하고 도메인 자격증명으로 변환. 누락 시 400 */
    public SocialCredential toCredential() {
        return switch (grantType) {
            case AUTHORIZATION_CODE -> {
                require(code, "code");
                require(redirectUri, "redirectUri");
                yield new SocialCredential.AuthorizationCode(
                        code, redirectUri, blankToNull(codeVerifier), blankToNull(nonce), blankToNull(state));
            }
            case ACCESS_TOKEN -> {
                require(accessToken, "accessToken");
                yield new SocialCredential.AccessToken(accessToken);
            }
            case ID_TOKEN -> {
                require(idToken, "idToken");
                yield new SocialCredential.IdToken(idToken, blankToNull(nonce));
            }
        };
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(field + "은(는) 필수입니다.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
