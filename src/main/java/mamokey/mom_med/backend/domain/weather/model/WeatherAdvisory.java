package mamokey.mom_med.backend.domain.weather.model;

import java.util.List;
import java.util.Map;

import mamokey.mom_med.backend.domain.weather.entity.WeatherRule;

/**
 * 부모 질환과 기상특보가 매칭된 결과 1건입니다.
 *
 * <p>ruleId는 Slice 07에서 같은 부모에게 같은 룰을 하루에 여러 번 보내지 않기 위한 dedup 키입니다.
 * message는 아직 호칭 치환을 하지 않은 원본 message_template이며, 실제 발송 문구 보정은 Slice 07에서 합니다.</p>
 */
public record WeatherAdvisory(
		Long ruleId,
		String diseaseCode,
		String diseaseName,
		String weatherAlert,
		String severity,
		String title,
		String message,
		List<String> patientActions,
		List<String> drugs,
		List<Map<String, Object>> sourceCitations,
		boolean requiresReview
) {

	public static WeatherAdvisory from(WeatherRule rule) {
		return new WeatherAdvisory(
				rule.getId(),
				rule.getDiseaseCode(),
				rule.getDiseaseName(),
				rule.getWeatherAlert(),
				rule.getSeverity(),
				rule.getTitle(),
				rule.getMessageTemplate(),
				rule.getPatientActions(),
				rule.getSpecificDrugsToNote(),
				rule.getSourceCitations(),
				rule.requiresReview()
		);
	}
}
