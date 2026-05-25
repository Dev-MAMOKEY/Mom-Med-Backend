package mamokey.mom_med.backend.domain.weather.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import mamokey.mom_med.backend.domain.weather.entity.WeatherRule;

/**
 * 관리자 검수 화면에서 사용하는 weather rule 요약 응답입니다.
 */
public record WeatherRuleAdminResponse(
		Long id,
		@JsonProperty("rule_version") String ruleVersion,
		@JsonProperty("disease_code") String diseaseCode,
		@JsonProperty("disease_name") String diseaseName,
		@JsonProperty("weather_alert") String weatherAlert,
		String severity,
		String title,
		@JsonProperty("message_template") String messageTemplate,
		@JsonProperty("general_knowledge_used") boolean generalKnowledgeUsed,
		@JsonProperty("requires_review") boolean requiresReview,
		@JsonProperty("approved_by") String approvedBy,
		@JsonProperty("approved_at") LocalDateTime approvedAt
) {

	public static WeatherRuleAdminResponse from(WeatherRule rule) {
		return new WeatherRuleAdminResponse(
				rule.getId(),
				rule.getRuleVersion(),
				rule.getDiseaseCode(),
				rule.getDiseaseName(),
				rule.getWeatherAlert(),
				rule.getSeverity(),
				rule.getTitle(),
				rule.getMessageTemplate(),
				rule.isGeneralKnowledgeUsed(),
				rule.requiresReview(),
				rule.getApprovedBy(),
				rule.getApprovedAt()
		);
	}
}
