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
				You are extracting drug-drug/drug-food interactions AND patient-class contraindications from a Korean drug label's 사용상의주의사항(NB_DOC_DATA) text.

				CONTEXT
				- Drug: %s
				- 주성분: %s
				- ATC: %s

				INPUT
				<text>
				%s
				</text>

				TASK A (drug_drug): 이 텍스트에서 "이 약과 다른 약물/식품 간 상호작용"으로 언급된 모든 약물·식품·약물군을 추출.

				TASK B (patient_class): 이 텍스트에서 "이 약을 투여하면 안 되는 또는 신중히 투여해야 하는 환자 분류"를 추출.
				예) "심한 간장애 환자에게는 투여하지 말 것", "소화성궤양 환자에게 신중히 투여" 등

				JSON SCHEMA:
				{
				  "drug": "<주성분 한글명>",
				  "interactions": [
				    {
				      "entry_type": "drug_drug",
				      "partner_drug_ko": "<한글 약물/식품/약물군>",
				      "partner_drug_en": "<영문, 없으면 null>",
				      "risk_level": "<동시투여피해야함 | 권장하지않음 | 주의 | 정보만>",
				      "reason_summary": "<1문장 한국어>",
				      "source_quote": "<원문 byte-for-byte 발췌 50~200자>"
				    },
				    {
				      "entry_type": "patient_class",
				      "patient_class_text": "<원문 환자 분류 표현 그대로, 예: '심한 간장애 환자'>",
				      "patient_class_kcd": "<AI 추론 KCD 범위, 예: 'K70-K77'. 해당 없으면 null>",
				      "risk_level": "<동시투여피해야함 | 권장하지않음>",
				      "reason_summary": "<1문장 한국어>",
				      "source_quote": "<원문 byte-for-byte 발췌 50~200자>"
				    }
				  ]
				}

				KCD 매핑 가이드 (patient_class_kcd 추론 시 사용):
				- 간장애 환자 → K70-K77
				- 신장(콩팥)장애 환자 → N17-N19
				- 심장기능저하 환자 → I50,I20-I25
				- 소화성궤양 환자 → K25-K27
				- 혈액 이상 환자 → D60-D64,D70-D77
				- 심한 고혈압 환자 → I10-I15
				- 당뇨 환자 → E10-E14
				- 갑상선 질환 환자 → E00-E07

				RULES:
				1. source_quote는 본문에 있는 그대로 (수정·요약 금지)
				2. drug_drug: 약물군(NSAIDs, CYP3A4 저해제 등)도 별개 entry, 식품(자몽 등) 포함, 자기 자신 제외
				3. patient_class: 임산부·과민증·알레르기 환자는 별도 시스템에서 처리하므로 제외
				4. entry_type 필드가 없는 기존 포맷(partner_drug_ko만 있는 경우)도 drug_drug로 처리됨
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
