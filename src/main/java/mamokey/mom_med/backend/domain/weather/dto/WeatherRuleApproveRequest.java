package mamokey.mom_med.backend.domain.weather.dto;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 의료진 검수 승인 요청입니다.
 *
 * <p>adjustedMessage가 있으면 seed 메시지를 그대로 쓰지 않고 승인자가 보정한 문구로 저장합니다.</p>
 */
public record WeatherRuleApproveRequest(
		@NotBlank @JsonProperty("approved_by") String approvedBy,
		@JsonProperty("adjusted_message") String adjustedMessage
) {
}
