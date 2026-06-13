package com.studychatbot.backend.global.ratelimit;

import com.studychatbot.backend.global.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 사용자별 LLM 호출 레이트리밋. Redis 시간 버킷 방식으로 카운트한다.
 * 키: ratelimit:{identifier}:min:{yyyyMMddHHmm}, ratelimit:{identifier}:day:{yyyyMMdd}
 * 버킷이 시간 단위로 새로 생성되고 TTL로 자동 만료되므로, 별도 리셋 로직이 필요 없다.
 */
@Service
public class RateLimitService {

    private static final String KEY_PREFIX = "ratelimit:";
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 버킷을 (창 길이 + 여유)만큼 살려두고 자동 만료시켜 Redis에 키가 무한정 쌓이지 않게 한다.
    // 분 버킷: 60초 + 여유 10초 / 일 버킷: 24시간 + 여유 1시간.
    private static final long MINUTE_TTL_SECONDS = 70L;
    private static final long DAY_TTL_SECONDS = 90_000L;

    // INCR과 EXPIRE를 하나의 원자적 실행으로 묶는다.
    // INCR만 하고 EXPIRE 직전에 장애가 나면 TTL 없는 영구 키가 남아 사용자가 영구 차단될 수 있다.
    // 첫 증가(결과==1)일 때만 EXPIRE를 걸어, 카운트 도중 만료창이 매 요청마다 밀리는 것도 방지한다.
    private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long perMinute;
    private final long perDay;

    // 한도는 프로파일(application*.properties)에서 주입받아 로컬 테스트 시 쉽게 낮출 수 있게 한다.
    public RateLimitService(StringRedisTemplate redisTemplate,
                            @Value("${app.ratelimit.per-minute:10}") long perMinute,
                            @Value("${app.ratelimit.per-day:100}") long perDay) {
        this.redisTemplate = redisTemplate;
        this.perMinute = perMinute;
        this.perDay = perDay;
    }

    /**
     * 사용자 식별자 기준으로 호출 한도를 검사하고 카운트를 1 증가시킨다.
     * 분당/일당 두 창 중 하나라도 초과하면 RateLimitExceededException(→429)을 던진다.
     *
     * 검사 순서: 분 창을 먼저 증가·검사하고, 통과한 경우에만 일 창을 증가시킨다.
     * 분 한도에 막힌 폭주 요청이 일 예산까지 소모하지 않게 하기 위함이다.
     */
    public void checkAndConsume(String identifier) {
        LocalDateTime now = LocalDateTime.now();

        String minuteKey = KEY_PREFIX + identifier + ":min:" + now.format(MINUTE_FORMAT);
        long minuteCount = increment(minuteKey, MINUTE_TTL_SECONDS);
        if (minuteCount > perMinute) {
            throw new RateLimitExceededException(
                    "분당 요청 한도(" + perMinute + "회)를 초과했습니다. 잠시 후 다시 시도해주세요.");
        }

        String dayKey = KEY_PREFIX + identifier + ":day:" + now.format(DAY_FORMAT);
        long dayCount = increment(dayKey, DAY_TTL_SECONDS);
        if (dayCount > perDay) {
            throw new RateLimitExceededException(
                    "일일 요청 한도(" + perDay + "회)를 초과했습니다. 내일 다시 시도해주세요.");
        }
    }

    private long increment(String key, long ttlSeconds) {
        Long count = redisTemplate.execute(INCR_WITH_TTL, List.of(key), String.valueOf(ttlSeconds));
        // 정상 경로에선 항상 1 이상이 반환된다. 방어적으로 null이면 0으로 보아 통과시킨다.
        return count == null ? 0L : count;
    }
}
