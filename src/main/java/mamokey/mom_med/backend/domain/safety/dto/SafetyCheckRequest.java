package mamokey.mom_med.backend.domain.safety.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * POST /v1/safety/check 요청 DTO입니다.
 *
 * <p>current_drugs와 new_drug는 모두 식약처 ITEM_SEQ입니다. API 계층에서는 이 값을
 * ref.drugs_master에서 조회해 main_ingr_norm을 꺼내고, DURRuleEngine은 그 정규화 성분명으로
 * 병용금기/노인주의를 검사합니다.</p>
 */
public record SafetyCheckRequest(
		@JsonProperty("parent_id")
		@NotBlank
		String parentId,

		@NotNull
		@Min(0)
		Integer age,

		@JsonProperty("current_drugs")
		@NotEmpty
		List<@NotBlank String> currentDrugs,

		@JsonProperty("new_drug")
		@NotBlank
		String newDrug
) {
}
