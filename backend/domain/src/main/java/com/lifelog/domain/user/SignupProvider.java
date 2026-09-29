package com.lifelog.domain.user;

import com.lifelog.domain.user.social.SocialProvider;

public enum SignupProvider {
    LOCAL, KAKAO, NAVER, GOOGLE;

    public static SignupProvider from(SocialProvider provider) {
        return switch (provider) {
            case KAKAO -> KAKAO;
            case NAVER -> NAVER;
            case GOOGLE -> GOOGLE;
        };
    }
}
