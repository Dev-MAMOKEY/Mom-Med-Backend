package mamokey.mom_med.backend.domain.weather.service;

import mamokey.mom_med.backend.domain.weather.dto.EtlRunStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 매일 05:30 KST 날씨 Advisory ETL 스케줄러 (Slice 07 v2).
 *
 * <p>실제 ETL 로직은 {@link WeatherEtlService}에 위임합니다.
 * 로컬에서는 application-local.yml의 cron을 "-"로 설정해 자동 실행을 비활성화합니다.</p>
 */
@Service
public class WeatherPushScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeatherPushScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WeatherEtlService weatherEtlService;

    public WeatherPushScheduler(WeatherEtlService weatherEtlService) {
        this.weatherEtlService = weatherEtlService;
    }

    @Scheduled(cron = "${app.scheduler.weather-push.cron:0 30 5 * * *}", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(KST);
        log.info("[스케줄러] 날씨 Advisory ETL 시작. date={}", today);
        EtlRunStats stats = weatherEtlService.runEtl(today);
        log.info("[스케줄러] 날씨 Advisory ETL 완료. {}", stats);
    }
}
