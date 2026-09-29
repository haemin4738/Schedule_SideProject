package com.lifelog.domain.user.social;

/** 클라이언트가 제공자에게서 받아 백엔드로 넘기는 자격증명 */
public sealed interface SocialCredential {

    /**
     * 웹(카카오/네이버/구글), Flutter 네이버 — 백엔드가 client_secret 으로 토큰 교환.
     * state 는 클라이언트가 콜백에서 대조를 마친 값 (네이버 토큰 요청 필수 파라미터)
     */
    record AuthorizationCode(String code, String redirectUri, String codeVerifier, String nonce, String state)
            implements SocialCredential {}

    /** Flutter 카카오 SDK — app_id 대조로 audience 검증 가능한 제공자만 허용 */
    record AccessToken(String token) implements SocialCredential {}

    /** Flutter 구글 SDK — JWKS 서명 + aud/iss/exp 검증 */
    record IdToken(String token, String nonce) implements SocialCredential {}
}
