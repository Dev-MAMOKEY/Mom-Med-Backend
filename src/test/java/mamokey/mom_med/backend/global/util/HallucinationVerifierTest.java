package mamokey.mom_med.backend.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import mamokey.mom_med.backend.global.util.HallucinationVerifier.VerifyResult;
import org.junit.jupiter.api.Test;

/**
 * HallucinationVerifier의 3단계 매칭 규칙을 검증하는 테스트입니다.
 *
 * <p>LLM 안전망은 "대충 맞는 것"보다 "왜 통과했는지 설명 가능한 것"이 중요합니다.
 * 그래서 exact, normalized, fuzzy, hallucinated를 각각 따로 테스트해 의도한 단계로 집계되는지 확인합니다.</p>
 */
class HallucinationVerifierTest {

	@Test
	void matchesExactQuote() {
		VerifyResult result = HallucinationVerifier.verify(
				extraction("source.md", "Amlodipine may interact with itraconazole."),
				Map.of("source.md", "Amlodipine may interact with itraconazole.")
		);

		assertThat(result.exact()).isEqualTo(1);
		assertThat(result.hallucinated()).isZero();
		assertThat(result.failures()).isEmpty();
	}

	@Test
	void matchesWhitespaceNormalizedQuote() {
		VerifyResult result = HallucinationVerifier.verify(
				extraction("source.md", "Amlodipine may interact with itraconazole."),
				Map.of("source.md", "Amlodipine\nmay   interact\twith itraconazole.")
		);

		assertThat(result.normalized()).isEqualTo(1);
		assertThat(result.failures()).singleElement()
				.satisfies(failure -> {
					assertThat(failure.reason()).isEqualTo("found_only_normalized");
					assertThat(failure.matchedVia()).isEqualTo("normalized");
				});
	}

	@Test
	void matchesFuzzyQuoteByHeadWindow() {
		String quote = "Warfarin combined with aspirin can increase bleeding risk in elderly patients.";
		String source = quote.substring(0, 45) + " after source text changed.";

		VerifyResult result = HallucinationVerifier.verify(
				extraction("source.md", quote),
				Map.of("source.md", source)
		);

		assertThat(result.fuzzy()).isEqualTo(1);
		assertThat(result.failures()).singleElement()
				.satisfies(failure -> {
					assertThat(failure.reason()).isEqualTo("found_only_fuzzy");
					assertThat(failure.matchedVia()).isEqualTo("fuzzy_head");
				});
	}

	@Test
	void marksMissingQuoteAsHallucinated() {
		VerifyResult result = HallucinationVerifier.verify(
				extraction("source.md", "This quote never appeared in the source."),
				Map.of("source.md", "Only grounded source text is present.")
		);

		assertThat(result.hallucinated()).isEqualTo(1);
		assertThat(result.failures()).singleElement()
				.satisfies(failure -> {
					assertThat(failure.reason()).isEqualTo("not_found");
					assertThat(failure.matchedVia()).isNull();
				});
	}

	private static Map<String, Object> extraction(String file, String quote) {
		// Slice 03의 예상 LLM 추출 구조를 단순화한 fixture입니다.
		return Map.of(
				"interactions", List.of(Map.of(
						"drug", "amlodipine",
						"source_citation", Map.of(
								"file", file,
								"quote", quote
						)
				))
		);
	}
}
