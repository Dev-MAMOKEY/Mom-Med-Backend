package mamokey.mom_med.backend.parent.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 응급카드 공개 QR 엔드포인트 Rate Limiter (Slice 08).
 *
 * <p>Bucket4j in-memory 버킷을 사용합니다.
 * <ul>
 *   <li>IP당 분당 10회</li>
 *   <li>토큰당 시간당 60회</li>
 *   <li>토큰당 일일 200회 — 초과 시 호출자가 카드를 자동 revoke합니다</li>
 * </ul>
 * 운영 환경에서는 Redis 기반 분산 버킷으로 교체하세요 (Redisson + Bucket4j Redis).</p>
 */
@Component
public class EmergencyCardRateLimiter {

    private final ConcurrentHashMap<String, Bucket> ipBuckets    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> tokenHourly  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> tokenDaily   = new ConcurrentHashMap<>();

    /** IP 한도 초과 여부 — true면 429 */
    public boolean isIpLimitExceeded(String ip) {
        return !ipBuckets
                .computeIfAbsent(ip, k -> buildBucket(10, Duration.ofMinutes(1)))
                .tryConsume(1);
    }

    /** 토큰 시간당 한도 초과 여부 — true면 429 */
    public boolean isTokenHourlyLimitExceeded(String token) {
        return !tokenHourly
                .computeIfAbsent(token, k -> buildBucket(60, Duration.ofHours(1)))
                .tryConsume(1);
    }

    /**
     * 토큰 일일 한도 초과 여부 — true면 429 + 자동 revoke.
     *
     * <p>시간당 한도보다 먼저 체크하지 않고 시간당 이후에 체크해야 합니다.</p>
     */
    public boolean isTokenDailyLimitExceeded(String token) {
        return !tokenDaily
                .computeIfAbsent(token, k -> buildBucket(200, Duration.ofDays(1)))
                .tryConsume(1);
    }

    private static Bucket buildBucket(long capacity, Duration refillPeriod) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, refillPeriod)
                        .build())
                .build();
    }
}
