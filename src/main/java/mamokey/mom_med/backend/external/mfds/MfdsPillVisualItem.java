package mamokey.mom_med.backend.external.mfds;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 식약처 낱알식별 API의 item입니다.
 */
public record MfdsPillVisualItem(
		@JsonProperty("ITEM_SEQ") String itemSeq,
		@JsonProperty("ITEM_IMAGE") String itemImage,
		@JsonProperty("DRUG_SHAPE") String drugShape,
		@JsonProperty("COLOR_CLASS1") String colorPrimary,
		@JsonProperty("COLOR_CLASS2") String colorSecondary,
		@JsonProperty("PRINT_FRONT") String printFront,
		@JsonProperty("PRINT_BACK") String printBack,
		@JsonProperty("LINE_FRONT") String lineFront,
		@JsonProperty("LINE_BACK") String lineBack,
		@JsonProperty("LENG_LONG") String lengthLong,
		@JsonProperty("LENG_SHORT") String lengthShort,
		@JsonProperty("THICK") String thickness,
		@JsonProperty("FORM_CODE_NAME") String formName,
		@JsonProperty("CHART") String chart,
		@JsonProperty("CHANGE_DATE") String changeDate
) {
}
