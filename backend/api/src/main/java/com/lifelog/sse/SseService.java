package com.lifelog.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        SseEmitter old = emitters.put(userId, emitter);
        if (old != null) old.complete();
        // two-arg remove: only removes if value still matches this emitter (prevents race with new subscriber)
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError(e -> emitters.remove(userId, emitter));
        return emitter;
    }

    public void publish(Long userId) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("REFRESH").data(""));
        } catch (IOException | IllegalStateException e) {
            // 연결이 끊겼거나 이미 완료·만료된 emitter — 다음 구독 때 새로 만든다
            emitters.remove(userId, emitter);
            log.debug("SSE 전송 실패로 구독 제거 userId={}", userId);
        }
    }
}
