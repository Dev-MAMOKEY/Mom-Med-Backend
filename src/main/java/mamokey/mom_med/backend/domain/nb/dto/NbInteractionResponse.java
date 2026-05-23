package mamokey.mom_med.backend.domain.nb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import mamokey.mom_med.backend.domain.nb.entity.NbInteraction;

/**
 * NB interaction 한 건을 API 응답으로 내려주는 DTO입니다.
 */
public record NbInteractionResponse(
		@JsonProperty("partner_drug_ko")
		String partnerDrugKo,
		@JsonProperty("partner_drug_norm")
		String partnerDrugNorm,
		@JsonProperty("partner_drug_en")
		String partnerDrugEn,
		@JsonProperty("is_drug_group")
		boolean drugGroup,
		@JsonProperty("risk_level")
		String riskLevel,
		@JsonProperty("reason_summary")
		String reasonSummary,
		@JsonProperty("source_quote")
		String sourceQuote
) {

	public static NbInteractionResponse from(NbInteraction interaction) {
		return new NbInteractionResponse(
				interaction.getPartnerDrugKo(),
				interaction.getPartnerDrugNorm(),
				interaction.getPartnerDrugEn(),
				interaction.isDrugGroup(),
				interaction.getRiskLevel(),
				interaction.getReasonSummary(),
				interaction.getSourceQuote()
		);
	}
}
