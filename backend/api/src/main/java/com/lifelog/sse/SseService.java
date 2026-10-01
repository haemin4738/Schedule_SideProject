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
        // 첫 이벤트를 보내기 전까지는 응답 헤더가 나가지 않아 클라이언트가 연결됐는지 알 수 없다.
        // 바로 CONNECTED 를 보내 연결(재연결) 즉시 끊겨 있던 동안의 변경을 다시 불러오게 한다 (모르는 이벤트는 클라이언트가 무시)
        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data(""));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(userId, emitter);
            log.debug("SSE 연결 직후 전송 실패 userId={}", userId);
        }
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
