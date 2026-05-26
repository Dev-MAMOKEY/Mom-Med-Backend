package mamokey.mom_med.backend.domain.weather.service;

import mamokey.mom_med.backend.domain.weather.dto.ParentWeatherAdvisoryResponse;
import mamokey.mom_med.backend.domain.weather.dto.ParentWeatherAdvisoryResponse.GridInfo;
import mamokey.mom_med.backend.domain.weather.dto.ParentWeatherAdvisoryResponse.ObservedTemps;
import mamokey.mom_med.backend.domain.weather.dto.WeatherAdvisoryResponse;
import mamokey.mom_med.backend.domain.weather.model.WeatherAdvisory;
import mamokey.mom_med.backend.domain.weather.repository.AdvisoryPushLogJdbcRepository;
import mamokey.mom_med.backend.domain.weather.repository.WeatherObservationDailyJdbcRepository;
import mamokey.mom_med.backend.domain.weather.repository.WeatherObservationDailyJdbcRepository.ObsResult;
import mamokey.mom_med.backend.parent.domain.PatientProfile;
import mamokey.mom_med.backend.parent.repository.PatientProfileRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 부모별 날씨 Advisory 조회 서비스 (Slice 07 v2).
 *
 * <p>실제 KMA 관측 DB 또는 simulate_alert 파라미터를 바탕으로 advisory를 조립합니다.</p>
 */
@Service
public class ParentWeatherAdvisoryService {

    private final PatientProfileRepository patientProfileRepository;
    private final WeatherObservationDailyJdbcRepository weatherObsRepo;
    private final WeatherDiseaseAdvisor weatherDiseaseAdvisor;
    private final AdvisoryPushLogJdbcRepository advisoryPushLogRepo;

    public ParentWeatherAdvisoryService(
            PatientProfileRepository patientProfileRepository,
            WeatherObservationDailyJdbcRepository weatherObsRepo,
            WeatherDiseaseAdvisor weatherDiseaseAdvisor,
            AdvisoryPushLogJdbcRepository advisoryPushLogRepo
    ) {
        this.patientProfileRepository = patientProfileRepository;
        this.weatherObsRepo = weatherObsRepo;
        this.weatherDiseaseAdvisor = weatherDiseaseAdvisor;
        this.advisoryPushLogRepo = advisoryPushLogRepo;
    }

    public ParentWeatherAdvisoryResponse getAdvisories(UUID parentId, LocalDate date,
                                                        List<String> simulateAlerts) {
        PatientProfile profile = patientProfileRepository.findById(parentId).orElse(null);
        GridInfo grid = profile != null && profile.getNx() != null
                ? new GridInfo(profile.getNx().intValue(), profile.getNy().intValue())
                : null;

        List<String> activeAlerts;
        ObservedTemps observed;

        if (!simulateAlerts.isEmpty()) {
            // simulate 모드: 입력된 특보를 그대로 사용
            activeAlerts = simulateAlerts;
            observed = null;
        } else if (grid != null) {
            // 실제 DB 관측 조회
            Optional<ObsResult> obs = weatherObsRepo.findByDateAndGrid(
                    date, profile.getNx(), profile.getNy());
            if (obs.isPresent()) {
                activeAlerts = obs.get().derivedAlerts();
                observed = new ObservedTemps(obs.get().tmx(), obs.get().tmn());
            } else {
                activeAlerts = List.of();
                observed = null;
            }
        } else {
            activeAlerts = List.of();
            observed = null;
        }

        List<String> diseaseCodes = weatherDiseaseAdvisor.getParentConditions(parentId);
        List<WeatherAdvisory> advisories = weatherDiseaseAdvisor.lookupRules(diseaseCodes, activeAlerts);

        List<WeatherAdvisoryResponse> advisoryResponses = advisories.stream()
                .map(a -> {
                    Optional<Instant> pushedAt = advisoryPushLogRepo.findLatestSentAt(parentId, a.ruleId(), date);
                    return WeatherAdvisoryResponse.from(a, pushedAt.isPresent(), pushedAt.orElse(null));
                })
                .toList();

        return new ParentWeatherAdvisoryResponse(parentId, date, grid, observed, activeAlerts, advisoryResponses);
    }
}
