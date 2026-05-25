package mamokey.mom_med.backend.domain.weather.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import mamokey.mom_med.backend.domain.weather.model.WeatherAdvisory;
import mamokey.mom_med.backend.domain.weather.repository.WeatherRuleRepository;
import mamokey.mom_med.backend.parent.repository.PatientConditionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부모 기저질환 코드와 활성 기상특보를 weather rule로 변환하는 룩업 모듈입니다.
 *
 * <p>검색은 disease_code와 weather_alert의 정확 매칭입니다. 룰이 없는 조합은 정상적으로 빈 배열을 반환합니다.</p>
 */
@Service
@Transactional(readOnly = true)
public class WeatherDiseaseAdvisor {

	private final WeatherRuleRepository weatherRuleRepository;
	private final PatientConditionRepository patientConditionRepository;

	public WeatherDiseaseAdvisor(
			WeatherRuleRepository weatherRuleRepository,
			PatientConditionRepository patientConditionRepository
	) {
		this.weatherRuleRepository = weatherRuleRepository;
		this.patientConditionRepository = patientConditionRepository;
	}

	public List<WeatherAdvisory> lookupRules(Collection<String> diseaseCodes, Collection<String> weatherAlerts) {
		if (diseaseCodes == null || diseaseCodes.isEmpty() || weatherAlerts == null || weatherAlerts.isEmpty()) {
			return List.of();
		}
		return weatherRuleRepository.findByDiseaseCodeInAndWeatherAlertIn(diseaseCodes, weatherAlerts).stream()
				.map(WeatherAdvisory::from)
				.toList();
	}

	public List<String> getParentConditions(UUID parentId) {
		return patientConditionRepository.findByParentIdAndDeletedAtIsNull(parentId).stream()
				.map(condition -> condition.getKcdCode())
				.filter(code -> code != null && !code.isBlank())
				.distinct()
				.toList();
	}
}
