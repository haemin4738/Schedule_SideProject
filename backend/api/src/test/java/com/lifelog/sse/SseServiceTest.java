package com.lifelog.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseServiceTest {

    private final SseService sseService = new SseService();

    @Test
    void publish_whenEmitterAlreadyCompleted_swallowsAndDoesNotThrow() {
        SseEmitter emitter = sseService.subscribe(1L);
        emitter.complete();
        // 완료된 emitter 로 보내면 IllegalStateException 이 난다 — 서비스는 삼키고 구독을 정리해야 한다
        assertThatThrownBy(() -> emitter.send(SseEmitter.event().name("REFRESH").data("")))
                .isInstanceOf(IllegalStateException.class);

        assertThatCode(() -> sseService.publish(1L)).doesNotThrowAnyException();
        assertThatCode(() -> sseService.publish(1L)).doesNotThrowAnyException();
    }

    @Test
    void publish_whenNoSubscriber_doesNothing() {
        assertThatCode(() -> sseService.publish(99L)).doesNotThrowAnyException();
    }

    @Test
    void subscribe_whenCalled_returnsEmitterAndDoesNotThrow() {
        // 연결 직후 CONNECTED 이벤트를 보낸다 — 핸들러가 붙기 전이라 emitter 가 버퍼링한다
        assertThatCode(() -> sseService.subscribe(2L)).doesNotThrowAnyException();
    }
}
