package mamokey.mom_med.backend.domain.weather.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 의료진 검수 승인 요청입니다.
 *
 * <p>adjustedMessage가 있으면 seed 메시지를 그대로 쓰지 않고 승인자가 보정한 문구로 저장합니다.</p>
 */
@Schema(description = "날씨 룰 의료진 승인 요청")
public record WeatherRuleApproveRequest(
		@Schema(description = "승인자 이름 또는 식별자 (필수)", example = "홍길동 원장")
		@NotBlank @JsonProperty("approved_by") String approvedBy,
		@Schema(description = "승인자가 보정한 메시지 문구. 미지정 시 seed 원본 메시지 유지.")
		@JsonProperty("adjusted_message") String adjustedMessage
) {
}
