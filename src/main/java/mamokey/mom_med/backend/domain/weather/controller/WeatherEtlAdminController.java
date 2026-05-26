package mamokey.mom_med.backend.domain.weather.controller;

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
    public ResponseEntity<RsData<EtlRunStats>> runEtl(
            @RequestBody(required = false) EtlRunRequest request
    ) {
        LocalDate targetDate = resolveDate(request);
        List<String> simulateAlerts = request != null ? request.effectiveSimulateAlerts() : List.of();
        EtlRunStats stats = weatherEtlService.runEtl(targetDate, simulateAlerts);
        return ResponseEntity.ok(RsData.ok(stats));
    }

    @GetMapping("/recent-pushes")
    public ResponseEntity<RsData<List<RecentPushEntry>>> recentPushes(
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
