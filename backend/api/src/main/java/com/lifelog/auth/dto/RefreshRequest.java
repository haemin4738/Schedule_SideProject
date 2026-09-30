package com.lifelog.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "refresh 토큰은 필수입니다.") String refreshToken) {}
