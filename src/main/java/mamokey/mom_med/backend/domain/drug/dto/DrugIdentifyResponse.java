package mamokey.mom_med.backend.domain.drug.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 약 식별 성공 시 반환하는 대표 응답 DTO입니다.
 *
 * <p>이 응답의 {@code item_seq}는 이후 DUR, NB, 부모 약장 등록에서 공통 식별자로
 * 사용됩니다. 화면 표시용 이름뿐 아니라 안전판정 조인 키인 {@code main_ingr_norm}도
 * 함께 내려줍니다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DrugIdentifyResponse(
		@JsonProperty("item_seq") String itemSeq,
		@JsonProperty("item_name") String itemName,
		@JsonProperty("main_ingr_en") String mainIngrEn,
		@JsonProperty("main_ingr_norm") String mainIngrNorm,
		@JsonProperty("atc_code") String atcCode,
		@JsonProperty("edi_code") String ediCode,
		@JsonProperty("specialty_type") String specialtyType,
		PillVisualResponse visual
) {
}
