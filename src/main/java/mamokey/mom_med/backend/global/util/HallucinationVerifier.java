package mamokey.mom_med.backend.global.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * LLM 추출 결과의 source quote가 실제 원문 파일에 존재하는지 검증합니다.
 * <p>
 * exact -> whitespace normalized -> 40자 fuzzy 순서로만 매칭해 검증 기준을 단순하고 재현 가능하게 둡니다.
 */
public final class HallucinationVerifier {

	private static final int FUZZY_WINDOW = 40;
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private HallucinationVerifier() {
	}

	public static VerifyResult verify(Map<String, ?> extraction, Map<String, String> sourceFiles) {
		List<SourceCitation> citations = new ArrayList<>();
		collectCitations(extraction, citations);

		int exact = 0;
		int normalized = 0;
		int fuzzy = 0;
		int hallucinated = 0;
		List<VerifyFailure> failures = new ArrayList<>();

		for (SourceCitation citation : citations) {
			String source = sourceFiles.get(citation.file());
			if (source == null || citation.quote() == null || citation.quote().isBlank()) {
				hallucinated++;
				failures.add(new VerifyFailure(citation.quote(), citation.file(), "not_found", null));
				continue;
			}

			MatchResult matchResult = match(citation.quote(), source);
			switch (matchResult.kind()) {
				case EXACT -> exact++;
				case NORMALIZED -> {
					normalized++;
					failures.add(new VerifyFailure(
							citation.quote(),
							citation.file(),
							"found_only_normalized",
							"normalized"
					));
				}
				case FUZZY_HEAD -> {
					fuzzy++;
					failures.add(new VerifyFailure(
							citation.quote(),
							citation.file(),
							"found_only_fuzzy",
							"fuzzy_head"
					));
				}
				case FUZZY_TAIL -> {
					fuzzy++;
					failures.add(new VerifyFailure(
							citation.quote(),
							citation.file(),
							"found_only_fuzzy",
							"fuzzy_tail"
					));
				}
				case NOT_FOUND -> {
					hallucinated++;
					failures.add(new VerifyFailure(citation.quote(), citation.file(), "not_found", null));
				}
			}
		}

		return new VerifyResult(citations.size(), exact, normalized, fuzzy, hallucinated, List.copyOf(failures));
	}

	private static MatchResult match(String quote, String source) {
		if (source.contains(quote)) {
			return new MatchResult(MatchKind.EXACT);
		}

		String normalizedSource = normalizeWhitespace(source);
		String normalizedQuote = normalizeWhitespace(quote);
		if (normalizedSource.contains(normalizedQuote)) {
			return new MatchResult(MatchKind.NORMALIZED);
		}

		if (quote.length() >= FUZZY_WINDOW) {
			String head = quote.substring(0, FUZZY_WINDOW);
			if (source.contains(head)) {
				return new MatchResult(MatchKind.FUZZY_HEAD);
			}

			String tail = quote.substring(quote.length() - FUZZY_WINDOW);
			if (source.contains(tail)) {
				return new MatchResult(MatchKind.FUZZY_TAIL);
			}
		}

		return new MatchResult(MatchKind.NOT_FOUND);
	}

	private static String normalizeWhitespace(String value) {
		return WHITESPACE.matcher(value).replaceAll(" ").strip();
	}

	@SuppressWarnings("unchecked")
	private static void collectCitations(Object value, List<SourceCitation> citations) {
		if (value instanceof Map<?, ?> map) {
			if (map.containsKey("quote")) {
				String quote = stringValue(map.get("quote"));
				String file = stringValue(map.get("file"));
				if (quote != null && file != null) {
					citations.add(new SourceCitation(file, quote));
				}
			}

			Object singleCitation = map.get("source_citation");
			if (singleCitation instanceof Map<?, ?> citationMap) {
				collectCitations(citationMap, citations);
			}

			Object manyCitations = map.get("source_citations");
			if (manyCitations instanceof Collection<?> collection) {
				for (Object citation : collection) {
					collectCitations(citation, citations);
				}
			}

			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if ("source_citation".equals(entry.getKey()) || "source_citations".equals(entry.getKey())) {
					continue;
				}
				Object nested = entry.getValue();
				if (nested instanceof Map<?, ?> || nested instanceof Collection<?>) {
					collectCitations(nested, citations);
				}
			}
			return;
		}

		if (value instanceof Collection<?> collection) {
			for (Object nested : collection) {
				collectCitations(nested, citations);
			}
		}
	}

	private static String stringValue(Object value) {
		return Objects.toString(value, null);
	}

	public record VerifyResult(
			int total,
			int exact,
			int normalized,
			int fuzzy,
			int hallucinated,
			List<VerifyFailure> failures
	) {
	}

	public record VerifyFailure(String quote, String file, String reason, String matchedVia) {
	}

	private record SourceCitation(String file, String quote) {
	}

	private record MatchResult(MatchKind kind) {
	}

	private enum MatchKind {
		EXACT,
		NORMALIZED,
		FUZZY_HEAD,
		FUZZY_TAIL,
		NOT_FOUND
	}
}
