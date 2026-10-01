package com.lifelog.event;

/** 사용자의 일정이 생성·수정·삭제됨 — 커밋 후 캐시 무효화·SSE 알림에 쓴다 */
public record EventsChangedEvent(Long userId) {
}
