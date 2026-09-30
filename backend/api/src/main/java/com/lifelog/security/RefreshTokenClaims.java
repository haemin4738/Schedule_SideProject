package com.lifelog.security;

import java.time.Instant;

/** 검증을 통과한 refresh 토큰의 클레임 */
public record RefreshTokenClaims(Long userId, String sessionId, String tokenId, Instant authTime, Instant expiresAt) {}
