package com.lifelog.specialday;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.specialday.SpecialDayData;
import com.lifelog.domain.specialday.SpecialDayKind;
import com.lifelog.domain.specialday.SpecialDayRepository;
import com.lifelog.domain.specialday.SpecialDaySource;
import com.lifelog.domain.specialday.SpecialDaySourceException;
import com.lifelog.domain.specialday.SpecialDayYearRepository;
import com.lifelog.specialday.dto.SpecialDayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 특일 조회. 조회 범위에 걸린 연도 중 아직 받지 않은 연도는 외부 출처에서 받아 저장한 뒤 DB 에서 조회한다(lazy).
 *
 * <ul>
 *   <li>외부 호출은 트랜잭션 밖에서 하고, 저장만 {@link SpecialDaySyncWriter} 가 한 트랜잭션으로 처리한다.</li>
 *   <li>같은 연도는 연도별 락 + 재확인으로 동시에 한 번만 받는다. 조회 요청은 락을 {@link #LOCK_TIMEOUT} 까지만
 *       기다리고, 넘으면 DB 데이터만 반환한다(정기 동기화는 끝까지 기다린다).</li>
 *   <li>받은 결과가 비정상(24절기 ≠ 24건, 공휴일 0건)이면 교체하지 않고 실패로 처리한다.</li>
 *   <li>실패한 연도는 {@link #FAILURE_BACKOFF} 동안 다시 시도하지 않는다(인메모리, 인스턴스 단위).</li>
 *   <li>외부 장애·키 미설정이면 이미 저장된 데이터(없으면 빈 목록)를 그대로 반환한다.</li>
 * </ul>
 */
@Slf4j
@Service
public class SpecialDayService {

    /** 특일 정보 API 가 제공하는 최초 연도 */
    static final int MIN_YEAR = 2004;
    /** 올해 기준 몇 년 뒤까지 받는지 (공휴일은 보통 전년도에 확정) */
    static final int MAX_YEARS_AHEAD = 2;
    static final int MAX_RANGE_DAYS = 366;
    static final Duration FAILURE_BACKOFF = Duration.ofMinutes(10);
    /** 조회 요청이 연도 락을 기다리는 최대 시간. 넘으면 DB 데이터만 반환 */
    static final Duration LOCK_TIMEOUT = Duration.ofSeconds(3);
    /** 24절기는 매년 정확히 24건 */
    static final int SOLAR_TERMS_PER_YEAR = 24;
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SpecialDaySource source;
    private final SpecialDayRepository specialDayRepository;
    private final SpecialDayYearRepository specialDayYearRepository;
    private final SpecialDaySyncWriter writer;
    private final Clock clock;
    private final Duration lockTimeout;

    private final ConcurrentMap<Integer, ReentrantLock> yearLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Instant> failedAt = new ConcurrentHashMap<>();

    @Autowired
    public SpecialDayService(SpecialDaySource source, SpecialDayRepository specialDayRepository,
                             SpecialDayYearRepository specialDayYearRepository, SpecialDaySyncWriter writer,
                             Clock clock) {
        this(source, specialDayRepository, specialDayYearRepository, writer, clock, LOCK_TIMEOUT);
    }

    /** 테스트에서 락 대기 시간을 줄이기 위한 생성자 */
    SpecialDayService(SpecialDaySource source, SpecialDayRepository specialDayRepository,
                      SpecialDayYearRepository specialDayYearRepository, SpecialDaySyncWriter writer,
                      Clock clock, Duration lockTimeout) {
        this.lockTimeout = lockTimeout;
        this.source = source;
        this.specialDayRepository = specialDayRepository;
        this.specialDayYearRepository = specialDayYearRepository;
        this.writer = writer;
        this.clock = clock;
    }

    /** from/to 양끝 포함, 최대 {@link #MAX_RANGE_DAYS}일. 날짜 → 종류 → 이름 순 */
    public List<SpecialDayResponse> list(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw BusinessException.badRequest("from, to 는 필수입니다.");
        }
        if (from.isAfter(to)) {
            throw BusinessException.badRequest("from 은 to 보다 늦을 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw BusinessException.badRequest("조회 기간은 최대 " + MAX_RANGE_DAYS + "일입니다.");
        }
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            ensureSynced(year);
        }
        return specialDayRepository.findByDateBetween(from, to).stream()
                .map(SpecialDayResponse::from)
                .toList();
    }

    /** 외부 출처 설정(인증키) 여부 */
    public boolean isSourceConfigured() {
        return source.isConfigured();
    }

    /** Asia/Seoul 기준 올해 */
    public int currentYear() {
        return LocalDate.ofInstant(clock.instant(), ZONE).getYear();
    }

    /**
     * 저장 여부·백오프와 관계없이 해당 연도를 다시 받는다(정기 동기화용). 실패하면 기존 데이터를 유지한다.
     *
     * @return 반영 성공 여부 (키 미설정·허용 연도 밖이면 false)
     */
    public boolean refresh(int year) {
        if (!isAllowedYear(year) || !source.isConfigured()) {
            return false;
        }
        ReentrantLock lock = yearLocks.computeIfAbsent(year, y -> new ReentrantLock());
        lock.lock();
        try {
            return sync(year);
        } finally {
            lock.unlock();
        }
    }

    private void ensureSynced(int year) {
        if (!isAllowedYear(year) || isSynced(year) || !source.isConfigured() || inBackoff(year)) {
            return;
        }
        ReentrantLock lock = yearLocks.computeIfAbsent(year, y -> new ReentrantLock());
        if (!tryLock(lock)) {
            // 다른 요청이 같은 연도를 받는 중 — 기다리지 않고 현재 DB 데이터로 응답
            log.warn("특일 연도 락 대기 시간 초과 — 저장된 데이터만 반환: year={}, timeoutMs={}",
                    year, lockTimeout.toMillis());
            return;
        }
        try {
            // 락 대기 중 다른 요청이 받았거나 실패했을 수 있으므로 재확인
            if (isSynced(year) || inBackoff(year)) {
                return;
            }
            sync(year);
        } finally {
            lock.unlock();
        }
    }

    private boolean tryLock(ReentrantLock lock) {
        try {
            return lock.tryLock(lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean sync(int year) {
        try {
            List<SpecialDayData> days = normalize(year, source.fetchYear(year));
            validate(year, days);
            writer.replace(year, days, LocalDateTime.ofInstant(clock.instant(), ZONE));
            failedAt.remove(year);
            log.info("특일 동기화 완료: year={}, count={}", year, days.size());
            return true;
        } catch (SpecialDaySourceException e) {
            // 메시지는 인증키·응답 본문이 없도록 만들어진 요약이다
            failedAt.put(year, clock.instant());
            log.warn("특일 외부 조회 실패 — 기존 데이터 유지: year={}, reason={}", year, e.getMessage());
            return false;
        } catch (DataAccessException e) {
            // DB 예외에는 인증키가 들어가지 않으므로 원인 분석을 위해 스택트레이스를 남긴다
            failedAt.put(year, clock.instant());
            log.error("특일 저장 실패(DB) — 기존 데이터 유지: year={}", year, e);
            return false;
        } catch (RuntimeException e) {
            // 출처 구현에서 올라온 예상 밖 예외일 수 있어(요청 URI 포함 가능) 타입만 남긴다
            failedAt.put(year, clock.instant());
            log.error("특일 저장 실패 — 기존 데이터 유지: year={}, cause={}", year, e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 부분·비정상 응답으로 연도를 교체하지 않도록 최소 조건을 확인한다.
     * 24절기가 정확히 24건이 아니거나 공휴일이 한 건도 없으면 실패로 처리해 기존 데이터를 유지한다.
     * (페이지 상한을 넘겨 잘린 응답은 출처 구현이 예외로 알린다)
     */
    private static void validate(int year, List<SpecialDayData> days) {
        long solarTerms = days.stream().filter(d -> d.kind() == SpecialDayKind.SOLAR_TERM).count();
        long holidays = days.stream().filter(d -> d.kind() == SpecialDayKind.HOLIDAY).count();
        if (solarTerms != SOLAR_TERMS_PER_YEAR) {
            throw new SpecialDaySourceException(
                    "special-day result rejected: year=" + year + ", solarTerms=" + solarTerms + " (expected 24)");
        }
        if (holidays == 0) {
            throw new SpecialDaySourceException("special-day result rejected: year=" + year + ", no holidays");
        }
    }

    /** 다른 연도 항목 제거, 같은 (날짜, 종류, 이름)은 처음 것만 */
    private static List<SpecialDayData> normalize(int year, List<SpecialDayData> days) {
        Map<SpecialDayData.Key, SpecialDayData> unique = new LinkedHashMap<>();
        for (SpecialDayData day : days) {
            if (day.date() != null && day.date().getYear() == year) {
                unique.putIfAbsent(day.key(), day);
            }
        }
        return List.copyOf(unique.values());
    }

    private boolean isAllowedYear(int year) {
        return year >= MIN_YEAR && year <= currentYear() + MAX_YEARS_AHEAD;
    }

    private boolean isSynced(int year) {
        return specialDayYearRepository.findByYear(year).isPresent();
    }

    private boolean inBackoff(int year) {
        Instant failed = failedAt.get(year);
        return failed != null && clock.instant().isBefore(failed.plus(FAILURE_BACKOFF));
    }
}
