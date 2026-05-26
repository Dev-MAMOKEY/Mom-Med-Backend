package mamokey.mom_med.backend.domain.weather.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mamokey.mom_med.backend.domain.weather.dto.EtlRunStats;
import mamokey.mom_med.backend.domain.weather.model.WeatherAdvisory;
import mamokey.mom_med.backend.domain.weather.model.WeatherObservation;
import mamokey.mom_med.backend.domain.weather.repository.AdvisoryPushLogJdbcRepository;
import mamokey.mom_med.backend.domain.weather.repository.WeatherObservationDailyJdbcRepository;
import mamokey.mom_med.backend.domain.weather.repository.WeatherObservationDailyJdbcRepository.ObsResult;
import mamokey.mom_med.backend.external.kma.KmaClient;
import mamokey.mom_med.backend.external.kma.KmaFcstItem;
import mamokey.mom_med.backend.infra.push.FcmPushSender;
import mamokey.mom_med.backend.infra.push.PushMessage;
import mamokey.mom_med.backend.parent.domain.PatientProfile;
import mamokey.mom_med.backend.parent.repository.DeviceTokenRepository;
import mamokey.mom_med.backend.parent.repository.PatientProfileRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 날씨 ETL 잡 핵심 로직 (Slice 07 v2).
 *
 * <p>스케줄러와 관리자 수동 실행(POST /v1/admin/weather/etl-run) 양쪽에서 호출합니다.
 * Redis 잡 레벨 락으로 같은 날짜 중복 실행을 방지합니다.</p>
 */
@Service
public class WeatherEtlService {

    private static final Logger log = LoggerFactory.getLogger(WeatherEtlService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<String, Integer> SEVERITY_ORDER = Map.of(
            "위험", 4, "경고", 3, "주의", 2, "관심", 1
    );

    private final RedissonClient redissonClient;
    private final PatientProfileRepository patientProfileRepository;
    private final WeatherObservationDailyJdbcRepository weatherObsRepo;
    private final AdvisoryPushLogJdbcRepository advisoryPushLogRepo;
    private final KmaClient kmaClient;
    private final WeatherAlertDeriver alertDeriver;
    private final WeatherDiseaseAdvisor weatherDiseaseAdvisor;
    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmPushSender fcmPushSender;
    private final ObjectMapper objectMapper;
    private final String encryptionKey;

    public WeatherEtlService(
            RedissonClient redissonClient,
            PatientProfileRepository patientProfileRepository,
            WeatherObservationDailyJdbcRepository weatherObsRepo,
            AdvisoryPushLogJdbcRepository advisoryPushLogRepo,
            KmaClient kmaClient,
            WeatherAlertDeriver alertDeriver,
            WeatherDiseaseAdvisor weatherDiseaseAdvisor,
            DeviceTokenRepository deviceTokenRepository,
            FcmPushSender fcmPushSender,
            ObjectMapper objectMapper,
            @Value("${app.encryption-key:}") String encryptionKey
    ) {
        this.redissonClient = redissonClient;
        this.patientProfileRepository = patientProfileRepository;
        this.weatherObsRepo = weatherObsRepo;
        this.advisoryPushLogRepo = advisoryPushLogRepo;
        this.kmaClient = kmaClient;
        this.alertDeriver = alertDeriver;
        this.weatherDiseaseAdvisor = weatherDiseaseAdvisor;
        this.deviceTokenRepository = deviceTokenRepository;
        this.fcmPushSender = fcmPushSender;
        this.objectMapper = objectMapper;
        this.encryptionKey = encryptionKey;
    }

    /**
     * 날씨 Advisory ETL 잡을 실행합니다.
     *
     * @param targetDate 대상 날짜 (보통 오늘 KST)
     * @return ETL 실행 통계 ({@code status="skipped_duplicate_run"} 이면 Redis 락 충돌로 스킵)
     */
    public EtlRunStats runEtl(LocalDate targetDate) {
        return runEtl(targetDate, List.of());
    }

    public EtlRunStats runEtl(LocalDate targetDate, List<String> simulateAlerts) {
        Instant startedAt = Instant.now();
        RLock jobLock = redissonClient.getLock("weather_etl:job:" + targetDate);
        boolean acquired;
        try {
            acquired = jobLock.tryLock(0, 30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("날씨 ETL 잡 락 획득 중 인터럽트. date={}", targetDate);
            return EtlRunStats.skippedDuplicate(startedAt);
        }
        if (!acquired) {
            log.warn("날씨 ETL 중복 실행 감지, 스킵. date={}", targetDate);
            return EtlRunStats.skippedDuplicate(startedAt);
        }
        try {
            return doRunEtl(targetDate, startedAt, simulateAlerts);
        } finally {
            try { jobLock.unlock(); } catch (Exception ignored) {}
        }
    }

    // ── 내부 ETL 실행 ──────────────────────────────────────────────────────────

    private EtlRunStats doRunEtl(LocalDate today, Instant startedAt, List<String> simulateAlerts) {
        String todayStr    = today.format(YYYYMMDD);
        String tomorrowStr = today.plusDays(1).format(YYYYMMDD);
        boolean isSimulated = !simulateAlerts.isEmpty();

        // 1) 데이터 공유 동의 + 격자 좌표 있는 부모 목록
        List<PatientProfile> parents = patientProfileRepository.findAllWithConsentAndGrid();
        log.info("[ETL] 대상 부모 수: {}, date={}, simulate={}", parents.size(), today, isSimulated);

        // 2) 고유 격자별 기상청 API 호출 + 관측 캐시 (simulate 시 KMA 호출 생략)
        Set<String> uniqueGrids = parents.stream()
                .map(p -> p.getNx() + ":" + p.getNy())
                .collect(Collectors.toSet());

        Map<String, ObsResult> obsCache = new HashMap<>();
        int gridsFetched = 0;

        for (String gridKey : uniqueGrids) {
            short nx = Short.parseShort(gridKey.split(":")[0]);
            short ny = Short.parseShort(gridKey.split(":")[1]);

            if (isSimulated) {
                // simulate 모드: KMA 호출 없이 주입된 특보 사용 (obsId=-1 로 placeholder)
                obsCache.put(gridKey, new ObsResult(-1L, null, null, simulateAlerts));
                continue;
            }

            Optional<ObsResult> cached = weatherObsRepo.findByDateAndGrid(today, nx, ny);
            if (cached.isPresent()) {
                obsCache.put(gridKey, cached.get());
                continue;
            }

            List<KmaFcstItem> items = kmaClient.getRawForecast(nx, ny, todayStr, "0500");
            if (items.isEmpty()) {
                log.warn("[ETL] KMA 응답 없음. nx={}, ny={}", nx, ny);
                continue;
            }

            WeatherObservation obs = alertDeriver.derive(items, todayStr, tomorrowStr);
            long obsId = weatherObsRepo.save(today, nx, ny, obs.tmx(), obs.tmn(),
                    obs.derivedAlerts(), todayStr, "0500");
            obsCache.put(gridKey, new ObsResult(obsId, obs.tmx(), obs.tmn(), obs.derivedAlerts()));
            gridsFetched++;
            log.info("[ETL] 격자 처리 완료. nx={}, ny={}, alerts={}", nx, ny, obs.derivedAlerts());
        }

        // 3) 부모별 Advisory + 푸시
        int sent = 0, fatigueSkipped = 0, reviewSkipped = 0;

        for (PatientProfile parent : parents) {
            String gridKey = parent.getNx() + ":" + parent.getNy();
            ObsResult obs = obsCache.get(gridKey);
            if (obs == null || !obs.hasAlerts()) continue;

            // 부모 레벨 Redis 락 — 동일 (parent, day) 1푸시 보장
            RLock parentLock = redissonClient.getLock(
                    "weather_etl:parent:" + parent.getParentId() + ":" + today);
            boolean parentAcquired;
            try {
                parentAcquired = parentLock.tryLock(0, 5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (!parentAcquired) continue;

            try {
                EtlParentResult result = processParent(parent, obs, today);
                sent          += result.sent;
                fatigueSkipped += result.fatigueSkipped;
                reviewSkipped  += result.reviewSkipped;
            } catch (Exception e) {
                log.warn("[ETL] 부모 처리 실패. parentId={}: {}", parent.getParentId(), e.getMessage());
            } finally {
                try { parentLock.unlock(); } catch (Exception ignored) {}
            }
        }

        EtlRunStats stats = new EtlRunStats(startedAt, gridsFetched, sent, fatigueSkipped, reviewSkipped, "completed");
        log.info("[ETL] 완료. {}", stats);
        return stats;
    }

    private EtlParentResult processParent(PatientProfile parent, ObsResult obs, LocalDate today) {
        // 기저질환 코드 조회
        List<String> diseaseCodes = weatherDiseaseAdvisor.getParentConditions(parent.getParentId());
        if (diseaseCodes.isEmpty()) return EtlParentResult.ZERO;

        // 룰 매칭
        List<WeatherAdvisory> advisories = weatherDiseaseAdvisor.lookupRules(diseaseCodes, obs.derivedAlerts());
        if (advisories.isEmpty()) return EtlParentResult.ZERO;

        // severity 최고 룰 1개 선택
        WeatherAdvisory top = advisories.stream()
                .max(Comparator.comparingInt(a -> SEVERITY_ORDER.getOrDefault(a.severity(), 0)))
                .orElse(null);
        if (top == null) return EtlParentResult.ZERO;

        // simulate 모드에서는 obsId를 null로 저장 (실제 관측 레코드 없음)
        Long obsId = obs.id() < 0 ? null : obs.id();

        // requires_review 룰 → skipped_review 기록 후 종료
        if (top.requiresReview()) {
            advisoryPushLogRepo.save(parent.getParentId(), top.ruleId(), obsId,
                    "{\"skipped_reason\":\"rule_under_review\"}", "skipped_review");
            return new EtlParentResult(0, 0, 1);
        }

        // 알람 피로 방지 — 오늘 이미 보낸 경우
        if (advisoryPushLogRepo.existsByParentRuleDate(parent.getParentId(), top.ruleId(), today)) {
            advisoryPushLogRepo.save(parent.getParentId(), top.ruleId(), obsId,
                    "{\"skipped_reason\":\"fatigue\"}", "skipped_fatigue");
            return new EtlParentResult(0, 1, 0);
        }

        // FCM 토큰 조회
        List<String> tokens = deviceTokenRepository.findDecryptedFcmTokens(parent.getParentId(), encryptionKey);
        if (tokens.isEmpty()) return EtlParentResult.ZERO;

        // 호칭 치환
        String body = top.message().replace("어머님", parent.getDisplayName());
        String payloadJson = buildPayloadJson(top, body, parent);

        // 푸시 발송
        String finalStatus = "failed";
        for (String token : tokens) {
            String result = fcmPushSender.send(token, new PushMessage(top.title(), body));
            if ("sent".equals(result) || "simulated".equals(result)) {
                finalStatus = result;
            }
        }

        advisoryPushLogRepo.save(parent.getParentId(), top.ruleId(), obsId, payloadJson, finalStatus);
        log.info("[ETL] 푸시 발송. parentId={}, ruleId={}, status={}", parent.getParentId(), top.ruleId(), finalStatus);

        return new EtlParentResult("sent".equals(finalStatus) || "simulated".equals(finalStatus) ? 1 : 0, 0, 0);
    }

    private String buildPayloadJson(WeatherAdvisory top, String body, PatientProfile parent) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "title", top.title(),
                    "body", body,
                    "data", Map.of(
                            "parent_id", parent.getParentId().toString(),
                            "rule_id", top.ruleId(),
                            "weather_alert", top.weatherAlert()
                    )
            ));
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    private record EtlParentResult(int sent, int fatigueSkipped, int reviewSkipped) {
        static final EtlParentResult ZERO = new EtlParentResult(0, 0, 0);
    }
}
