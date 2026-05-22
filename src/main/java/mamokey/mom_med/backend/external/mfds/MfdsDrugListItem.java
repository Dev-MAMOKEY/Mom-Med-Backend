package mamokey.mom_med.backend.external.mfds;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 식약처 제품 허가정보 목록 API의 item입니다.
 */
public record MfdsDrugListItem(
		@JsonProperty("ITEM_SEQ") String itemSeq,
		@JsonProperty("ITEM_NAME") String itemName,
		@JsonProperty("ENTP_NAME") String entpName,
		@JsonProperty("ITEM_PERMIT_DATE") String itemPermitDate,
		@JsonProperty("SPCLTY_PBLC") String specialtyType,
		@JsonProperty("EDI_CODE") String ediCode,
		@JsonProperty("BIZRNO") String bizrno,
		@JsonProperty("CHANGE_DATE") String changeDate
) {
}
