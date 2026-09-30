package com.lifelog.infrastructure.user;

import com.lifelog.domain.user.session.AuthSessionUnavailableException;
import com.lifelog.domain.user.session.RefreshRotationOutcome;
import com.lifelog.domain.user.session.RefreshRotationResult;
import com.lifelog.domain.user.session.RefreshSession;
import com.lifelog.domain.user.session.RefreshSessionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * refresh 세션 allowlist 를 Redis 에 저장한다. 캐시용 RedisTemplate/직렬화기와 분리하기 위해 StringRedisTemplate 을 사용한다.
 *
 * <p>키 (해시태그 {@code {u:<userId>}} 로 한 사용자의 키를 같은 슬롯에 둔다):
 * <ul>
 *     <li>{@code auth:refresh:{u:<userId>}:session:<sid>} — Hash {jti, prevJti, rotatedAt(epoch ms), authTime(epoch ms)}, TTL = 세션 수명</li>
 *     <li>{@code auth:refresh:{u:<userId>}:sessions} — Set(sid), TTL = 살아있는 멤버 세션 TTL 의 최댓값</li>
 *     <li>{@code auth:refresh:{u:<userId>}:revoked:<sid>} — String "REUSE", TTL = 폐기 시점 세션의 남은 TTL. 읽어도 삭제하지 않는다</li>
 * </ul>
 *
 * <p>모든 변경은 Lua 스크립트로 원자적으로 수행한다. 스크립트는 인덱스 멤버(sid)로부터 세션/tombstone 키를
 * <b>동적으로 조립</b>해 접근한다(KEYS 로 선언하지 않은 키). 이는 standalone Redis 에서는 문제없고,
 * Cluster 에서도 모든 키가 같은 해시태그 {@code {u:<userId>}} 를 가지므로 같은 슬롯이라는 가정에 의존한다.
 * 키 형식을 바꿀 때 해시태그를 유지해야 한다.
 *
 * <p><b>주의</b>: KEYS 로 선언하지 않은 키에 접근하므로 스크립트에 {@code #!lua} shebang(flags)을 추가하지 않는다
 * (shebang 이 있으면 선언되지 않은 키 접근이 거부된다). 해시태그 {@code {u:<userId>}} 를 유지한다.
 */
@Slf4j
@Component
public class RedisRefreshSessionStore implements RefreshSessionStore {

    static final String KEY_PREFIX = "auth:refresh:";
    static final String FIELD_JTI = "jti";
    static final String FIELD_PREV_JTI = "prevJti";
    static final String FIELD_ROTATED_AT = "rotatedAt";
    static final String FIELD_AUTH_TIME = "authTime";
    static final String TOMBSTONE_VALUE = "REUSE";

    // ROTATE_SCRIPT 반환 코드 ({code, jti} 의 code)
    private static final int CODE_NOT_FOUND = 0;
    private static final int CODE_ROTATED = 1;
    private static final int CODE_REISSUED = 2;
    private static final int CODE_REUSE_DETECTED = 3;
    private static final int CODE_REVOKED = 4;
    private static final int CODE_PREVIOUS_AFTER_GRACE = 5;

    // 인덱스의 죽은 멤버(세션 키 만료) 정리 후 인덱스 TTL 을 살아있는 세션 TTL 최댓값으로 맞춘다. 비면 삭제
    private static final String SYNC_INDEX_FUNCTION = """
            local function syncIndex(indexKey, sessionPrefix)
                local maxTtl = 0
                for _, member in ipairs(redis.call('SMEMBERS', indexKey)) do
                    local ttl = redis.call('PTTL', sessionPrefix .. member)
                    if ttl == -2 then
                        redis.call('SREM', indexKey, member)
                    elseif ttl > maxTtl then
                        maxTtl = ttl
                    end
                end
                if redis.call('SCARD', indexKey) == 0 then
                    redis.call('DEL', indexKey)
                elseif maxTtl > 0 then
                    redis.call('PEXPIRE', indexKey, maxTtl)
                end
            end
            """;

    // KEYS: 1=session, 2=index / ARGV: 1=sid, 2=jti, 3=authTime(ms), 4=ttl(ms), 5=session key prefix
    private static final RedisScript<Long> CREATE_SCRIPT = RedisScript.of(SYNC_INDEX_FUNCTION + """
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'jti', ARGV[2], 'authTime', ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('SADD', KEYS[2], ARGV[1])
            syncIndex(KEYS[2], ARGV[5])
            return 1
            """, Long.class);

    // KEYS: 1=session, 2=index, 3=tombstone(sid)
    // ARGV: 1=sid, 2=presented jti, 3=new jti, 4=ttl(ms), 5=now(ms), 6=overlap(ms), 7=session key prefix, 8=tombstone key prefix
    // 반환: {code, jti} — 0=NOT_FOUND, 1=ROTATED(jti=새 jti), 2=REISSUED(jti=현재 jti), 3=REUSE_DETECTED, 4=REVOKED,
    //       5=PREVIOUS_AFTER_GRACE. jti 가 없으면 ''
    // 주의: KEYS 로 선언하지 않은 키(인덱스 멤버로 조립한 세션/tombstone 키)에 접근한다.
    //       `#!lua` shebang(flags) 추가 금지, 해시태그 {u:<userId>} 유지
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ROTATE_SCRIPT = RedisScript.of(SYNC_INDEX_FUNCTION + """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                if redis.call('EXISTS', KEYS[3]) == 1 then
                    return {4, ''}
                end
                return {0, ''}
            end
            local current = redis.call('HGET', KEYS[1], 'jti')
            if current == ARGV[2] then
                redis.call('HSET', KEYS[1], 'jti', ARGV[3], 'prevJti', ARGV[2], 'rotatedAt', ARGV[5])
                redis.call('PEXPIRE', KEYS[1], ARGV[4])
                redis.call('SADD', KEYS[2], ARGV[1])
                syncIndex(KEYS[2], ARGV[7])
                return {1, ARGV[3]}
            end
            local prev = redis.call('HGET', KEYS[1], 'prevJti')
            if prev and prev == ARGV[2] then
                local rotatedAt = tonumber(redis.call('HGET', KEYS[1], 'rotatedAt'))
                if rotatedAt and (tonumber(ARGV[5]) - rotatedAt) <= tonumber(ARGV[6]) then
                    -- 겹침 구간: 회전하지 않고 현재 jti 로 재발급. rotatedAt 불변(구간 연장 없음), TTL 은 늘리기만
                    if redis.call('PTTL', KEYS[1]) < tonumber(ARGV[4]) then
                        redis.call('PEXPIRE', KEYS[1], ARGV[4])
                    end
                    redis.call('SADD', KEYS[2], ARGV[1])
                    syncIndex(KEYS[2], ARGV[7])
                    return {2, current}
                end
                -- 겹침 구간 경과: 해당 세션만 삭제 (tombstone 없음)
                redis.call('DEL', KEYS[1])
                redis.call('SREM', KEYS[2], ARGV[1])
                if redis.call('SCARD', KEYS[2]) == 0 then
                    redis.call('DEL', KEYS[2])
                end
                return {5, ''}
            end
            -- 재사용 탐지(두 세대 이상 전 jti): 사용자 전체 세션 삭제 + 세션별 남은 TTL 로 tombstone
            local members = redis.call('SMEMBERS', KEYS[2])
            local seen = {}
            table.insert(members, ARGV[1])
            for _, member in ipairs(members) do
                if not seen[member] then
                    seen[member] = true
                    local sessionKey = ARGV[7] .. member
                    local ttl = redis.call('PTTL', sessionKey)
                    if ttl == -1 then
                        ttl = tonumber(ARGV[4])
                    end
                    if ttl > 0 then
                        redis.call('SET', ARGV[8] .. member, 'REUSE', 'PX', ttl)
                    end
                    redis.call('DEL', sessionKey)
                end
            end
            redis.call('DEL', KEYS[2])
            return {3, ''}
            """, List.class);

    // KEYS: 1=session, 2=index / ARGV: 1=sid. tombstone 은 남기지 않는다
    private static final RedisScript<Long> REVOKE_SCRIPT = RedisScript.of("""
            local deleted = redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[1])
            if redis.call('SCARD', KEYS[2]) == 0 then
                redis.call('DEL', KEYS[2])
            end
            return deleted
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRefreshSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void create(RefreshSession session, Duration ttl) {
        if (session == null || session.userId() == null || isBlank(session.sessionId())
                || isBlank(session.tokenId()) || session.authTime() == null) {
            throw new IllegalArgumentException("session fields are required");
        }
        if (!isPositive(ttl)) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        Long userId = session.userId();
        String sessionId = session.sessionId();
        execute(() -> redis.execute(CREATE_SCRIPT,
                List.of(sessionKey(userId, sessionId), indexKey(userId)),
                sessionId,
                session.tokenId(),
                String.valueOf(session.authTime().toEpochMilli()),
                String.valueOf(ttl.toMillis()),
                sessionKeyPrefix(userId)));
    }

    @Override
    public RefreshRotationOutcome rotate(Long userId, String sessionId, String presentedTokenId, String newTokenId,
                                         Duration ttl, Instant now, Duration overlapWindow) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (overlapWindow == null || overlapWindow.isNegative()) {
            throw new IllegalArgumentException("overlapWindow must not be negative");
        }
        if (userId == null || isBlank(sessionId) || isBlank(presentedTokenId) || isBlank(newTokenId)
                || !isPositive(ttl)) {
            return RefreshRotationOutcome.of(RefreshRotationResult.NOT_FOUND);
        }
        List<?> result = execute(() -> redis.execute(ROTATE_SCRIPT,
                List.of(sessionKey(userId, sessionId), indexKey(userId), tombstoneKey(userId, sessionId)),
                sessionId,
                presentedTokenId,
                newTokenId,
                String.valueOf(ttl.toMillis()),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(overlapWindow.toMillis()),
                sessionKeyPrefix(userId),
                tombstoneKeyPrefix(userId)));
        return toOutcome(result);
    }

    // 로그아웃은 jti 를 확인하지 않고 세션(sid)을 삭제한다(의도): 서명된 같은 세션의 어떤 토큰으로든 폐기할 수 있는 건
    // 안전한 방향이고, 오래된 토큰을 /refresh 에 내면 어차피 전체 폐기된다 (RFC 7009 — 무효 토큰도 200)
    @Override
    public boolean revoke(Long userId, String sessionId) {
        if (userId == null || isBlank(sessionId)) {
            return false;
        }
        Long deleted = execute(() -> redis.execute(REVOKE_SCRIPT,
                List.of(sessionKey(userId, sessionId), indexKey(userId)),
                sessionId));
        return deleted != null && deleted > 0;
    }

    static String userPrefix(Long userId) {
        return KEY_PREFIX + "{u:" + userId + "}:";
    }

    static String sessionKeyPrefix(Long userId) {
        return userPrefix(userId) + "session:";
    }

    static String tombstoneKeyPrefix(Long userId) {
        return userPrefix(userId) + "revoked:";
    }

    static String sessionKey(Long userId, String sessionId) {
        return sessionKeyPrefix(userId) + sessionId;
    }

    static String indexKey(Long userId) {
        return userPrefix(userId) + "sessions";
    }

    static String tombstoneKey(Long userId, String sessionId) {
        return tombstoneKeyPrefix(userId) + sessionId;
    }

    static RefreshRotationOutcome toOutcome(List<?> reply) {
        if (reply == null || reply.isEmpty() || !(reply.get(0) instanceof Number number)) {
            log.warn("refresh 회전 스크립트 응답 형식 오류 → NOT_FOUND 처리");
            return RefreshRotationOutcome.of(RefreshRotationResult.NOT_FOUND);
        }
        int code = number.intValue();
        String tokenId = reply.size() > 1 && reply.get(1) instanceof String value && !value.isEmpty() ? value : null;
        return switch (code) {
            case CODE_ROTATED -> new RefreshRotationOutcome(RefreshRotationResult.ROTATED, tokenId);
            case CODE_REISSUED -> new RefreshRotationOutcome(RefreshRotationResult.REISSUED, tokenId);
            case CODE_PREVIOUS_AFTER_GRACE -> RefreshRotationOutcome.of(RefreshRotationResult.PREVIOUS_AFTER_GRACE);
            case CODE_REUSE_DETECTED -> RefreshRotationOutcome.of(RefreshRotationResult.REUSE_DETECTED);
            case CODE_REVOKED -> RefreshRotationOutcome.of(RefreshRotationResult.REVOKED);
            case CODE_NOT_FOUND -> RefreshRotationOutcome.of(RefreshRotationResult.NOT_FOUND);
            default -> {
                log.warn("refresh 회전 스크립트 알 수 없는 코드 → NOT_FOUND 처리: code={}", code);
                yield RefreshRotationOutcome.of(RefreshRotationResult.NOT_FOUND);
            }
        };
    }

    private static <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException e) {
            // 응답(503)과 ERROR 로그는 GlobalExceptionHandler 가 담당한다 — 여기서는 중복 ERROR 를 피해 WARN 만 남긴다
            log.warn("refresh 세션 저장소 오류: type={}", e.getClass().getSimpleName());
            throw new AuthSessionUnavailableException("refresh session store unavailable", e);
        }
    }

    private static boolean isPositive(Duration duration) {
        // PEXPIRE 0 은 키를 즉시 삭제하므로 밀리초 단위로 양수여야 한다
        return duration != null && duration.toMillis() > 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
