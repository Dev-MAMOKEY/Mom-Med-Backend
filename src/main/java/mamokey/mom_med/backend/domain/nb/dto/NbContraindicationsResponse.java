package mamokey.mom_med.backend.domain.nb.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import mamokey.mom_med.backend.domain.nb.entity.NbExtraction;
import mamokey.mom_med.backend.domain.nb.entity.NbInteraction;
import mamokey.mom_med.backend.domain.nb.service.NbExtractionResult;

/**
 * GET /v1/drugs/{itemSeq}/contraindications 응답 DTO입니다.
 *
 * <p>검증된 NB extraction과 펼쳐진 interactions를 함께 반환해, 운영자가 추출 결과와
 * SafetyJudge가 참조하는 행을 한 번에 확인할 수 있게 합니다.</p>
 */
public record NbContraindicationsResponse(
		@JsonProperty("item_seq")
		String itemSeq,
		String drug,
		List<NbInteractionResponse> interactions,
		boolean verified,
		@JsonProperty("extracted_at")
		OffsetDateTime extractedAt,
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

	public static NbContraindicationsResponse from(NbExtractionResult result) {
		NbExtraction extraction = result.extraction();
		return new NbContraindicationsResponse(
				extraction.getItemSeq(),
				drugName(extraction, result.interactions()),
				result.interactions().stream().map(NbInteractionResponse::from).toList(),
				extraction.isVerified(),
				extraction.getExtractedAt(),
				extraction.getLlmModel(),
				extraction.getPromptVersion(),
				extraction.getTokenInput(),
				extraction.getTokenOutput(),
				extraction.getCostUsd()
		);
	}

	private static String drugName(NbExtraction extraction, List<NbInteraction> interactions) {
		if (!interactions.isEmpty()) {
			return interactions.getFirst().getDrugName();
		}
		Map<String, Object> json = extraction.getExtractionJson();
		Object drug = json == null ? null : json.get("drug");
		return drug == null ? extraction.getItemSeq() : drug.toString();
	}
}
