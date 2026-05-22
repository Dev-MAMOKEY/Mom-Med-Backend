package mamokey.mom_med.backend.domain.drug.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 동명이품 후보 한 건을 프론트에 보여주기 위한 응답 DTO입니다.
 *
 * <p>식약처 목록 API가 여러 제품을 반환하면 서버가 임의로 하나를 고르지 않고,
 * 후보 목록을 내려보내 사용자가 정확한 제품을 선택하게 합니다.</p>
 */
public record DrugCandidateResponse(
		@JsonProperty("item_seq") String itemSeq,
		@JsonProperty("item_name") String itemName,
		@JsonProperty("entp_name") String entpName,
		@JsonProperty("specialty_type") String specialtyType,
		@JsonProperty("edi_code") String ediCode
) {
}
