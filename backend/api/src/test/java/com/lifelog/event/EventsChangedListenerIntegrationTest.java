package com.lifelog.event;

import com.lifelog.sse.SseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 일정 변경 이벤트가 실제 트랜잭션 커밋 이후에만 캐시 무효화·SSE 알림으로 이어지는지 검증한다 (실제 트랜잭션 매니저 사용).
 */
@SpringBootTest
@ActiveProfiles("test")
class EventsChangedListenerIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EventCacheService eventCacheService;
    @MockitoBean
    private SseService sseService;

    @Test
    void onEventsChanged_whenTransactionCommits_evictsAndPublishesOnlyAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new EventsChangedEvent(7L));
            // 커밋 전에는 아무 것도 하지 않는다
            verifyNoInteractions(eventCacheService, sseService);
        });

        var order = inOrder(eventCacheService, sseService);
        order.verify(eventCacheService).evictAll();
        order.verify(sseService).publish(7L);
    }

    @Test
    void onEventsChanged_whenTransactionRollsBack_doesNothing() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new EventsChangedEvent(7L));
            status.setRollbackOnly();
        });

        verifyNoInteractions(eventCacheService, sseService);
    }

    @Test
    void onEventsChanged_whenNoTransaction_doesNothing() {
        // @TransactionalEventListener 기본값(fallbackExecution=false) — 서비스 메서드는 항상 트랜잭션 안에서 발행한다
        eventPublisher.publishEvent(new EventsChangedEvent(7L));

        verifyNoInteractions(eventCacheService, sseService);
    }
}
