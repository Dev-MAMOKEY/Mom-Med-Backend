package mamokey.mom_med.backend.domain.nb.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import mamokey.mom_med.backend.domain.nb.service.NbExtractionResult;

/**
 * POST /v1/drugs/{itemSeq}/extract-nb smoke trigger 응답 DTO입니다.
 */
public record NbExtractionTriggerResponse(
		@JsonProperty("item_seq")
		String itemSeq,
		boolean verified,
		@JsonProperty("interaction_count")
		int interactionCount,
		@JsonProperty("from_cache")
		boolean fromCache,
		@JsonProperty("llm_model")
		String llmModel,
		@JsonProperty("prompt_version")
		String promptVersion,
		@JsonProperty("token_input")
		Integer tokenInput,
		@JsonProperty("token_output")
		Integer tokenOutput,
		@JsonProperty("cost_usd")
		BigDecimal costUsd
) {

	public static NbExtractionTriggerResponse from(NbExtractionResult result) {
		return new NbExtractionTriggerResponse(
				result.extraction().getItemSeq(),
				result.extraction().isVerified(),
				result.interactions().size(),
				result.fromCache(),
				result.extraction().getLlmModel(),
				result.extraction().getPromptVersion(),
				result.extraction().getTokenInput(),
				result.extraction().getTokenOutput(),
				result.extraction().getCostUsd()
		);
	}
}
