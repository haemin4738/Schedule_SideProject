package com.lifelog.domain.user.social;

import java.time.Duration;
import java.util.Optional;

/**
 * 계정 통합 대기 정보 저장소 (1회용 link token).
 * 원문 토큰은 저장하지 않는다. 저장소 장애 시 {@link SocialAuthException} (SERVICE_UNAVAILABLE).
 */
public interface PendingSocialLinkStore {

    /** 새 1회용 토큰을 발급하고 원문 토큰을 반환 */
    String issue(PendingSocialLink link, Duration ttl);

    Optional<PendingSocialLink> find(String token);

    /** 비밀번호 오답 시 시도 횟수를 원자적으로 증가시키고 증가 후 값을 반환 (토큰 없으면 0) */
    int incrementAttempts(String token);

    /** 원자적으로 삭제. 이번 호출이 삭제했으면 true (1회용 보장) */
    boolean consume(String token);
}
