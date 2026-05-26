package mamokey.mom_med.backend.domain.weather.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import mamokey.mom_med.backend.domain.weather.model.WeatherAdvisory;

/**
 * 날씨 advisory 응답 DTO (Slice 07 v2).
 *
 * <p>already_pushed / pushed_at 은 오늘 해당 룰이 이미 발송됐는지 나타냅니다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        @JsonProperty("requires_review") boolean requiresReview,
        @JsonProperty("already_pushed") boolean alreadyPushed,
        @JsonProperty("pushed_at") Instant pushedAt
) {

    public static WeatherAdvisoryResponse from(WeatherAdvisory advisory) {
        return from(advisory, false, null);
    }

    public static WeatherAdvisoryResponse from(WeatherAdvisory advisory, boolean alreadyPushed, Instant pushedAt) {
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
                advisory.requiresReview(),
                alreadyPushed,
                pushedAt
        );
    }
}
