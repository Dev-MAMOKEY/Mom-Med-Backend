package mamokey.mom_med.backend.domain.weather.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import mamokey.mom_med.backend.domain.weather.dto.EtlRunRequest;
import mamokey.mom_med.backend.domain.weather.dto.EtlRunStats;
import mamokey.mom_med.backend.domain.weather.repository.AdvisoryPushLogJdbcRepository;
import mamokey.mom_med.backend.domain.weather.repository.AdvisoryPushLogJdbcRepository.RecentPushEntry;
import mamokey.mom_med.backend.domain.weather.service.WeatherEtlService;
import mamokey.mom_med.backend.global.rsdata.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 날씨 ETL 관리자 API (Slice 07 v2).
 *
 * <ul>
 *   <li>POST /v1/admin/weather/etl-run — 수동 ETL 실행</li>
 *   <li>GET  /v1/admin/weather/recent-pushes — 최근 N일 푸시 이력 조회</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/admin/weather")
@Tag(name = "Weather ETL Admin", description = "날씨 ETL 수동 실행 및 푸시 이력 조회 (Slice 07)")
public class WeatherEtlAdminController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WeatherEtlService weatherEtlService;
    private final AdvisoryPushLogJdbcRepository advisoryPushLogRepo;

    public WeatherEtlAdminController(WeatherEtlService weatherEtlService,
                                     AdvisoryPushLogJdbcRepository advisoryPushLogRepo) {
        this.weatherEtlService = weatherEtlService;
        this.advisoryPushLogRepo = advisoryPushLogRepo;
    }

    @PostMapping("/etl-run")
    @Operation(
            summary = "날씨 ETL 수동 실행",
            description = """
                    매일 05:30 KST 스케줄러와 동일한 ETL 잡을 수동으로 트리거합니다.
                    - `base_date` 미지정 시 오늘(KST) 기준으로 실행합니다. 형식: `YYYYMMDD`.
                    - `simulate_alerts` 지정 시 KMA API를 호출하지 않고 해당 특보를 강제 주입합니다 (로컬 테스트용).
                      예: `["폭염경보", "한파주의보"]`
                    - Redis 잡 레벨 락으로 동일 날짜 중복 실행을 방지합니다.
                      이미 실행 중이면 `status=skipped_duplicate_run`을 반환합니다.
                    """
    )
    public ResponseEntity<RsData<EtlRunStats>> runEtl(
            @RequestBody(required = false) EtlRunRequest request
    ) {
        LocalDate targetDate = resolveDate(request);
        List<String> simulateAlerts = request != null ? request.effectiveSimulateAlerts() : List.of();
        EtlRunStats stats = weatherEtlService.runEtl(targetDate, simulateAlerts);
        return ResponseEntity.ok(RsData.ok(stats));
    }

    @GetMapping("/recent-pushes")
    @Operation(
            summary = "최근 N일 푸시 이력 조회",
            description = "advisory_push_log에서 최근 N일간의 푸시 발송 이력을 반환합니다. 기본값은 7일입니다."
    )
    public ResponseEntity<RsData<List<RecentPushEntry>>> recentPushes(
            @Parameter(description = "조회 기간 (일 단위, 기본값 7)")
            @RequestParam(defaultValue = "7") int days
    ) {
        List<RecentPushEntry> entries = advisoryPushLogRepo.findRecentPushes(days);
        return ResponseEntity.ok(RsData.ok(entries));
    }

    private LocalDate resolveDate(EtlRunRequest request) {
        if (request == null || request.baseDate() == null || request.baseDate().isBlank()) {
            return LocalDate.now(KST);
        }
        try {
            return LocalDate.parse(request.baseDate(), YYYYMMDD);
        } catch (DateTimeParseException e) {
            return LocalDate.now(KST);
        }
    }
}
