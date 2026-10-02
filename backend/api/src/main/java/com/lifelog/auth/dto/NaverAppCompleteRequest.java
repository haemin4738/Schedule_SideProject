package com.lifelog.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 네이버 앱 로그인 완료. ticket = app-callback 이 앱에 넘긴 값, codeVerifier = 시작 때 challenge 를 만든 원문 (RFC 7636 문자 집합) */
public record NaverAppCompleteRequest(
        @NotBlank(message = "ticket은 필수입니다.")
        @Size(max = 128, message = "ticket이 너무 깁니다.")
        String ticket,

        @NotBlank(message = "codeVerifier는 필수입니다.")
        @Pattern(regexp = "^[A-Za-z0-9._~-]{43,128}$", message = "codeVerifier 형식이 올바르지 않습니다.")
        String codeVerifier
) {}
