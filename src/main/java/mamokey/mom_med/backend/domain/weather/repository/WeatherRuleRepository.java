package mamokey.mom_med.backend.domain.weather.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import mamokey.mom_med.backend.domain.weather.entity.WeatherRule;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ref.weather_rules 조회 Repository입니다.
 *
 * <p>룩업은 LIKE가 아니라 disease_code와 weather_alert의 정확 매칭으로 수행합니다.
 * 이 방식이 Slice 07에서 매일 많은 부모를 처리할 때 인덱스를 가장 안정적으로 사용합니다.</p>
 */
public interface WeatherRuleRepository extends JpaRepository<WeatherRule, Long> {

	Optional<WeatherRule> findByRuleVersionAndDiseaseCodeAndWeatherAlert(
			String ruleVersion,
			String diseaseCode,
			String weatherAlert
	);

	List<WeatherRule> findByDiseaseCodeInAndWeatherAlertIn(
			Collection<String> diseaseCodes,
			Collection<String> weatherAlerts
	);

	List<WeatherRule> findByGeneralKnowledgeUsedTrueAndApprovedAtIsNullOrderByIdAsc();
}
