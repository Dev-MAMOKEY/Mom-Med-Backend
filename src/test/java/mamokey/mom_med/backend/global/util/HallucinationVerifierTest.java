package mamokey.mom_med.backend.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import mamokey.mom_med.backend.global.util.HallucinationVerifier.VerifyResult;
import org.junit.jupiter.api.Test;

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
