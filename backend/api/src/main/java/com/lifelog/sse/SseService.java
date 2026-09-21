package com.lifelog.sse;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));
        return emitter;
    }

    public void publish(Long userId) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("REFRESH").data(""));
        } catch (IOException e) {
            emitters.remove(userId);
        }
    }
}
