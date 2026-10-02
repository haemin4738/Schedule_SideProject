package com.lifelog.domain.user.social;

/** 서버가 code 교환을 마친 앱 로그인 결과. codeChallenge = BASE64URL(SHA-256(앱 verifier)) */
public record AppLoginTicket(String codeChallenge, SocialUserInfo userInfo) {}
