package com.lifelog.auth.dto;

import com.lifelog.domain.user.social.SocialProvider;

/**
 * 소셜 로그인 결과. HTTP 200 고정이며 클라이언트는 status 로 분기한다.
 * LOGGED_IN → token 존재, LINK_REQUIRED → link 존재 (비밀번호 확인 후 /auth/social/link 호출)
 */
public record SocialLoginResponse(Status status, boolean newUser, TokenResponse token, LinkInfo link) {

    public enum Status { LOGGED_IN, LINK_REQUIRED }

    public record LinkInfo(String linkToken, SocialProvider provider, String maskedEmail, long expiresInSeconds) {}

    public static SocialLoginResponse loggedIn(TokenResponse token, boolean newUser) {
        return new SocialLoginResponse(Status.LOGGED_IN, newUser, token, null);
    }

    public static SocialLoginResponse linkRequired(LinkInfo link) {
        return new SocialLoginResponse(Status.LINK_REQUIRED, false, null, link);
    }
}
