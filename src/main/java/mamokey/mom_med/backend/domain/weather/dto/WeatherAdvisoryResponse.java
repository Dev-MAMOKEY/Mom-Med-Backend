package mamokey.mom_med.backend.domain.weather.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import mamokey.mom_med.backend.domain.weather.model.WeatherAdvisory;

/**
 * 날씨 advisory 응답 DTO (Slice 07 v2).
 *
 * <p>already_pushed / pushed_at 은 오늘 해당 룰이 이미 발송됐는지 나타냅니다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "날씨 건강 advisory 항목")
public record WeatherAdvisoryResponse(
        @Schema(description = "룰 ID (ref.weather_rules.id)")
        @JsonProperty("rule_id") Long ruleId,
        @Schema(description = "기저질환 KCD 코드", example = "I10")
        @JsonProperty("disease_code") String diseaseCode,
        @Schema(description = "기저질환명", example = "고혈압")
        @JsonProperty("disease_name") String diseaseName,
        @Schema(description = "매칭된 기상특보 유형", example = "폭염경보")
        @JsonProperty("weather_alert") String weatherAlert,
        @Schema(description = "심각도. 위험 > 경고 > 주의 > 관심", example = "경고")
        String severity,
        @Schema(description = "푸시 제목")
        String title,
        @Schema(description = "호칭 치환 후 본문 메시지")
        String message,
        @Schema(description = "권장 행동 지침 목록")
        @JsonProperty("patient_actions") List<String> patientActions,
        @Schema(description = "주의 약물 목록")
        List<String> drugs,
        @Schema(description = "출처 문헌 목록")
        @JsonProperty("source_citations") List<Map<String, Object>> sourceCitations,
        @Schema(description = "의료진 미승인 룰 여부. true이면 푸시 미발송.")
        @JsonProperty("requires_review") boolean requiresReview,
        @Schema(description = "오늘 해당 룰 푸시가 이미 발송됐는지 여부")
        @JsonProperty("already_pushed") boolean alreadyPushed,
        @Schema(description = "최초 발송 시각 (already_pushed=true일 때만 존재)")
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
