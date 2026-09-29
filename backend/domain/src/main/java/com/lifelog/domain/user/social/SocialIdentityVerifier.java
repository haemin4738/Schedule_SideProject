package com.lifelog.domain.user.social;

/** 제공자 자격증명을 검증하고 사용자 정보를 돌려주는 포트. 실패 시 {@link SocialAuthException} */
public interface SocialIdentityVerifier {
    SocialUserInfo verify(SocialProvider provider, SocialCredential credential);
}
