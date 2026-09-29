package com.lifelog.domain.user.social;

/** 비밀번호 확인 후 연결할 소셜 계정 정보 */
public record PendingSocialLink(Long userId, SocialProvider provider, String providerUserId) {}
