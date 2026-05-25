package mamokey.mom_med.backend.domain.weather.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import mamokey.mom_med.backend.domain.weather.model.WeatherAdvisory;

/**
 * 날씨 advisory 응답 DTO입니다.
 *
 * <p>ruleId는 프론트 표시뿐 아니라 Slice 07 푸시 중복 방지에도 필요하므로 반드시 포함합니다.</p>
 */
public record WeatherAdvisoryResponse(
		@JsonProperty("rule_id") Long ruleId,
		@JsonProperty("disease_code") String diseaseCode,
		@JsonProperty("disease_name") String diseaseName,
		@JsonProperty("weather_alert") String weatherAlert,
		String severity,
		String title,
		String message,
		@JsonProperty("patient_actions") List<String> patientActions,
		List<String> drugs,
		@JsonProperty("source_citations") List<Map<String, Object>> sourceCitations,
		@JsonProperty("requires_review") boolean requiresReview
) {

	public static WeatherAdvisoryResponse from(WeatherAdvisory advisory) {
		return new WeatherAdvisoryResponse(
				advisory.ruleId(),
				advisory.diseaseCode(),
				advisory.diseaseName(),
				advisory.weatherAlert(),
				advisory.severity(),
				advisory.title(),
				advisory.message(),
				advisory.patientActions(),
				advisory.drugs(),
				advisory.sourceCitations(),
				advisory.requiresReview()
		);
	}
}
