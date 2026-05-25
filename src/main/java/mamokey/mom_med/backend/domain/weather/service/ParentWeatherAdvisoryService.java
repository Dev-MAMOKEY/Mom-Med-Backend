package mamokey.mom_med.backend.domain.weather.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import mamokey.mom_med.backend.domain.weather.dto.ParentWeatherAdvisoryResponse;
import mamokey.mom_med.backend.domain.weather.dto.WeatherAdvisoryResponse;
import org.springframework.stereotype.Service;

/**
 * 부모별 날씨 advisory API를 조립하는 application service입니다.
 *
 * <p>Slice 06은 기상청 API를 호출하지 않으므로 현재는 simulate_alert 요청값을 활성 특보처럼 받아 룰 매칭만 검증합니다.
 * 실제 오늘 특보 조회와 알람 피로 방지는 Slice 07에서 이 서비스 앞뒤로 붙습니다.</p>
 */
@Service
public class ParentWeatherAdvisoryService {

	private final WeatherDiseaseAdvisor weatherDiseaseAdvisor;

	public ParentWeatherAdvisoryService(WeatherDiseaseAdvisor weatherDiseaseAdvisor) {
		this.weatherDiseaseAdvisor = weatherDiseaseAdvisor;
	}

	public ParentWeatherAdvisoryResponse getAdvisories(UUID parentId, LocalDate date, List<String> weatherAlerts) {
		List<String> diseaseCodes = weatherDiseaseAdvisor.getParentConditions(parentId);
		List<WeatherAdvisoryResponse> advisories = weatherDiseaseAdvisor.lookupRules(diseaseCodes, weatherAlerts).stream()
				.map(WeatherAdvisoryResponse::from)
				.toList();
		return new ParentWeatherAdvisoryResponse(parentId, date, weatherAlerts == null ? List.of() : weatherAlerts, advisories);
	}
}
