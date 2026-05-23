package mamokey.mom_med.backend.domain.drug.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 알약 사진과 외형 정보를 내려주는 응답 DTO입니다.
 *
 * <p>사용자는 약 이름만으로 실물을 확신하기 어렵기 때문에, 이미지 URL과 각인/색상/크기
 * 정보를 함께 제공해 실제 약장 속 알약과 대조할 수 있게 합니다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PillVisualResponse(
		@JsonProperty("image_url") String imageUrl,
		@JsonProperty("drug_shape") String drugShape,
		@JsonProperty("color_primary") String colorPrimary,
		@JsonProperty("color_secondary") String colorSecondary,
		@JsonProperty("print_front") String printFront,
		@JsonProperty("print_back") String printBack,
		List<BigDecimal> length,
		@JsonProperty("form_name") String formName
) {
}
