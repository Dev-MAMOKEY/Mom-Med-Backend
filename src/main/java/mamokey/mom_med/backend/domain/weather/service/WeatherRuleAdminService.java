package mamokey.mom_med.backend.domain.weather.service;

import java.util.List;

import mamokey.mom_med.backend.domain.weather.dto.WeatherRuleAdminListResponse;
import mamokey.mom_med.backend.domain.weather.dto.WeatherRuleAdminResponse;
import mamokey.mom_med.backend.domain.weather.dto.WeatherRuleApproveRequest;
import mamokey.mom_med.backend.domain.weather.entity.WeatherRule;
import mamokey.mom_med.backend.domain.weather.repository.WeatherRuleRepository;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 날씨 룰 의료진 검수용 서비스입니다.
 */
@Service
public class WeatherRuleAdminService {

	private final WeatherRuleRepository weatherRuleRepository;

	public WeatherRuleAdminService(WeatherRuleRepository weatherRuleRepository) {
		this.weatherRuleRepository = weatherRuleRepository;
	}

	@Transactional(readOnly = true)
	public WeatherRuleAdminListResponse getRules(String status) {
		List<WeatherRule> rules = "needs_review".equals(status)
				? weatherRuleRepository.findByGeneralKnowledgeUsedTrueAndApprovedAtIsNullOrderByIdAsc()
				: weatherRuleRepository.findAll();
		List<WeatherRuleAdminResponse> responses = rules.stream()
				.map(WeatherRuleAdminResponse::from)
				.toList();
		return new WeatherRuleAdminListResponse(responses.size(), responses);
	}

	@Transactional
	public WeatherRuleAdminResponse approve(Long ruleId, WeatherRuleApproveRequest request) {
		WeatherRule rule = weatherRuleRepository.findById(ruleId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "weather rule을 찾을 수 없습니다: " + ruleId));
		rule.approve(request.approvedBy(), request.adjustedMessage());
		return WeatherRuleAdminResponse.from(rule);
	}
}
