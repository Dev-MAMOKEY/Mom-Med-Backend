package mamokey.mom_med.backend.domain.nb.prompt;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;

/**
 * NB_DOC_DATA interaction 추출용 Gemini prompt v1입니다.
 *
 * <p>프롬프트는 JSON schema와 source_quote 원문 보존 규칙을 명확히 고정합니다.
 * source_quote가 정제 평문과 달라지면 HallucinationVerifier에서 실패하므로,
 * 모델에게 요약이 아닌 원문 발췌를 요구하는 것이 가장 중요합니다.</p>
 */
public final class NbExtractionPrompt {

	public static final String PROMPT_VERSION = "v1";

	private NbExtractionPrompt() {
	}

	public static String build(DrugMaster drug, String cleanedNbDocData) {
		return """
				You are extracting drug-drug and drug-food interactions from a Korean drug label's 사용상의주의사항(NB_DOC_DATA) text.

				CONTEXT
				- Drug: %s
				- 주성분: %s
				- ATC: %s

				INPUT
				<text>
				%s
				</text>

				TASK: 이 텍스트에서 "이 약과 다른 약물/식품 간 상호작용"으로 언급된 모든 약물·식품·약물군을 추출해, 아래 JSON 스키마에 맞춰 반환.

				JSON SCHEMA:
				{
				  "drug": "<주성분 한글명>",
				  "interactions": [
				    {
				      "partner_drug_ko": "<한글 약물/식품/약물군>",
				      "partner_drug_en": "<영문, 없으면 null>",
				      "risk_level": "<동시투여피해야함 | 권장하지않음 | 주의 | 정보만>",
				      "reason_summary": "<1문장 한국어>",
				      "source_quote": "<원문 byte-for-byte 발췌 50~200자>"
				    }
				  ]
				}

				RULES:
				1. source_quote는 본문에 있는 그대로 (수정·요약 금지)
				2. 약물군(NSAIDs, CYP3A4 저해제 등)도 별개 entry
				3. 식품(자몽 등) 포함
				4. 자기 자신 제외
				5. 출력은 strict JSON, 첫 글자 '{'.
				""".formatted(
				nullToEmpty(drug.getItemName()),
				nullToEmpty(drug.getMainIngrEn()),
				nullToEmpty(drug.getAtcCode()),
				cleanedNbDocData
		);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
