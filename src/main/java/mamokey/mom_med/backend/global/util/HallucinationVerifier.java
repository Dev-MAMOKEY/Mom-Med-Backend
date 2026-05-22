package mamokey.mom_med.backend.global.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * LLM 추출 결과의 source quote가 실제 원문 파일에 존재하는지 검증하는 공통 유틸입니다.
 *
 * <p>Slice 03에서 NB 문서를 LLM으로 구조화할 때, 모델이 원문에 없는 문장을 만들어낼 수 있습니다.
 * 이 클래스는 추출 결과 안의 source_citation.quote를 모아 source_files 본문과 대조해
 * exact -> whitespace normalized -> fuzzy 40자 순서로 검증합니다.</p>
 *
 * <p>유지보수 주의: fuzzy 단계는 편의상 "앞 40자 또는 뒤 40자"만 확인합니다.
 * 더 느슨한 유사도 알고리즘을 넣으면 거짓 양성이 늘 수 있으므로 안전성 요구사항을 먼저 확인해야 합니다.</p>
 */
public final class HallucinationVerifier {

	private static final int FUZZY_WINDOW = 40;
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private HallucinationVerifier() {
		// 상태가 없는 유틸 클래스이므로 인스턴스 생성을 막습니다.
	}

	/**
	 * extraction 안의 모든 citation을 찾아 sourceFiles와 비교합니다.
	 *
	 * @param extraction LLM이 만든 구조화 결과입니다. 어느 위치든 source_citation/source_citations를 찾습니다.
	 * @param sourceFiles 파일명과 원문 본문을 담은 map입니다.
	 * @return 전체 citation 수와 매칭 단계별 집계, exact가 아닌 항목 목록입니다.
	 */
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
		// 1단계: 원문 그대로 포함되어 있으면 가장 신뢰도 높은 exact로 판정합니다.
		if (source.contains(quote)) {
			return new MatchResult(MatchKind.EXACT);
		}

		// 2단계: 줄바꿈, 탭, 여러 공백 차이만 있는 경우 normalized로 판정합니다.
		String normalizedSource = normalizeWhitespace(source);
		String normalizedQuote = normalizeWhitespace(quote);
		if (normalizedSource.contains(normalizedQuote)) {
			return new MatchResult(MatchKind.NORMALIZED);
		}

		// 3단계: 긴 quote에서 앞/뒤 40자가 남아 있으면 일부 편집된 인용으로 보고 fuzzy로 판정합니다.
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

			// 단일 citation과 citation 배열을 모두 지원해 Slice 03 schema 변경에 조금 더 유연하게 대응합니다.
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

	/**
	 * 검증 결과 집계입니다.
	 * failures에는 exact가 아닌 normalized/fuzzy/hallucinated 항목을 담아 후속 안전망에서 재검토할 수 있게 합니다.
	 */
	public record VerifyResult(
			int total,
			int exact,
			int normalized,
			int fuzzy,
			int hallucinated,
			List<VerifyFailure> failures
	) {
	}

	/**
	 * exact 검증을 통과하지 못한 quote의 상세 정보입니다.
	 */
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
