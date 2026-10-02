package com.lifelog.auth.dto;

/** 앱이 그대로 여는 네이버 authorize URL (state 포함). expiresInSeconds 안에 로그인을 마쳐야 한다 */
public record NaverAppStartResponse(String authorizeUrl, long expiresInSeconds) {}
