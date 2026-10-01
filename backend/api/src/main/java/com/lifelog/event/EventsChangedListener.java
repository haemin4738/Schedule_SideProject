package com.lifelog.event;

import com.lifelog.sse.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 일정 변경이 커밋된 뒤에 캐시를 비우고 SSE 로 알린다.
 * 트랜잭션 안에서 비우면 커밋 전에 들어온 조회가 옛 데이터를 다시 캐시하고,
 * SSE 를 받은 클라이언트가 커밋 전 데이터를 다시 불러올 수 있다.
 */
@Component
@RequiredArgsConstructor
public class EventsChangedListener {

    private final EventCacheService eventCacheService;
    private final SseService sseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventsChanged(EventsChangedEvent event) {
        eventCacheService.evictAll();
        sseService.publish(event.userId());
    }
}
