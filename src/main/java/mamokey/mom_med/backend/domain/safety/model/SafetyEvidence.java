package mamokey.mom_med.backend.domain.safety.model;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 약물 안전 판정의 근거를 표현하는 공통 record입니다.
 *
 * <p>Slice 02에서는 DUR 병용금기와 노인주의 근거를 담고, Slice 03 이후 NB/알레르기 근거가
 * 추가되어도 같은 응답 구조로 확장할 수 있습니다. JSON 필드는 프론트 합의 형태에 맞춰
 * snake_case로 직렬화합니다.</p>
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
		String itemSeqSource
) {

	public SafetyEvidence(
			String source,
			String type,
			String ingredientA,
			String ingredientB,
			String reason,
			String gazetteNo,
			LocalDate gazetteDate
	) {
		this(source, type, ingredientA, ingredientB, reason, gazetteNo, gazetteDate, null, null, null, null);
	}

	public String dedupeKey() {
		String left = ingredientA == null ? "" : ingredientA;
		String right = ingredientB == null ? "" : ingredientB;
		if (right.isBlank()) {
			return source + "|" + type + "|" + left + "|" + nullToEmpty(partnerDrug) + "|" + nullToEmpty(itemSeqSource);
		}
		return left.compareTo(right) <= 0
				? source + "|" + type + "|" + left + "|" + right + "|" + nullToEmpty(partnerDrug) + "|" + nullToEmpty(itemSeqSource)
				: source + "|" + type + "|" + right + "|" + left + "|" + nullToEmpty(partnerDrug) + "|" + nullToEmpty(itemSeqSource);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
