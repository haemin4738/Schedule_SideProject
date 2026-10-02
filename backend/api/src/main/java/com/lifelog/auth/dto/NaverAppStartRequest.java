package com.lifelog.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 네이버 앱 로그인 시작. codeChallenge = BASE64URL(SHA-256(codeVerifier)), 패딩 없음 43자 */
public record NaverAppStartRequest(
        @NotBlank(message = "codeChallenge는 필수입니다.")
        @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "codeChallenge 형식이 올바르지 않습니다.")
        String codeChallenge
) {}
