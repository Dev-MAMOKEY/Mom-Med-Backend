package mamokey.mom_med.backend.domain.safety.model;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 약물 안전 판정의 근거를 표현하는 공통 record입니다.
 *
 * <p>Slice 02: DUR 병용금기·노인주의. Slice 03: NB drug_drug.
 * Slice 05 확장: NB patient_class (patient_class/matched_kcd_codes),
 * DUR 연령금기·임부금기, 알레르기(allergen/allergen_severity).
 * JSON 필드는 snake_case이며 null 필드는 직렬화에서 제외됩니다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SafetyEvidence(
		String source,
		String type,
		@JsonProperty("ingredient_a")
		String ingredientA,
		@JsonProperty("ingredient_b")
		String ingredientB,
		String reason,
		@JsonProperty("gazette_no")
		String gazetteNo,
		@JsonProperty("gazette_date")
		LocalDate gazetteDate,
		@JsonProperty("risk_level")
		String riskLevel,
		@JsonProperty("partner_drug")
		String partnerDrug,
		String quote,
		@JsonProperty("item_seq_source")
		String itemSeqSource,
		// Slice 05 확장 필드 ─────────────────────────────────────────────────
		/** NB patient_class: 환자 분류 원문 (예: "심한 간장애 환자") */
		@JsonProperty("patient_class")
		String patientClass,
		/** NB patient_class: 부모 기저질환과 매칭된 KCD 코드 목록 */
		@JsonProperty("matched_kcd_codes")
		List<String> matchedKcdCodes,
		/** 알레르기: 원인 알레르겐 이름 */
		String allergen,
		/** 알레르기: 중증도 (severe/moderate/mild/unknown) */
		@JsonProperty("allergen_severity")
		String allergenSeverity
) {

	/** DUR 병용금기·노인주의·연령금기·임부금기용 7-인수 편의 생성자. */
	public SafetyEvidence(
			String source,
			String type,
			String ingredientA,
			String ingredientB,
			String reason,
			String gazetteNo,
			LocalDate gazetteDate
	) {
		this(source, type, ingredientA, ingredientB, reason, gazetteNo, gazetteDate,
				null, null, null, null, null, null, null, null);
	}

	public String dedupeKey() {
		String left = ingredientA == null ? "" : ingredientA;
		String right = ingredientB == null ? "" : ingredientB;
		String patientClassKey = patientClass == null ? "" : patientClass;
		String allergenKey = allergen == null ? "" : allergen;
		if (right.isBlank()) {
			return source + "|" + type + "|" + left + "|" + nullToEmpty(partnerDrug) + "|"
					+ nullToEmpty(itemSeqSource) + "|" + patientClassKey + "|" + allergenKey;
		}
		return left.compareTo(right) <= 0
				? source + "|" + type + "|" + left + "|" + right + "|" + nullToEmpty(partnerDrug) + "|"
				+ nullToEmpty(itemSeqSource) + "|" + patientClassKey + "|" + allergenKey
				: source + "|" + type + "|" + right + "|" + left + "|" + nullToEmpty(partnerDrug) + "|"
				+ nullToEmpty(itemSeqSource) + "|" + patientClassKey + "|" + allergenKey;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
