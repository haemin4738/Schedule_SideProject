package com.lifelog.domain.user.social;

/** 제공자 검증을 통과한 사용자 정보. email 은 제공되지 않으면 null */
public record SocialUserInfo(
        SocialProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String name
) {}
