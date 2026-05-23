package mamokey.mom_med.backend.domain.nb.util;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 식약처 NB_DOC_DATA 원문을 LLM 입력에 적합한 평문으로 정제하는 유틸입니다.
 *
 * <p>NB_DOC_DATA는 XML/HTML 조각, CDATA, entity가 섞여 들어올 수 있습니다. Gemini에는
 * 구조 태그보다 실제 주의사항 문장만 주는 편이 안정적이므로, 태그를 제거하고 공백을 정규화합니다.
 * source_quote 검증은 이 정제된 평문을 기준으로 수행합니다.</p>
 */
public final class NbDocDataCleaner {

	private static final Pattern CDATA_OPEN = Pattern.compile("<!\\[CDATA\\[");
	private static final Pattern CDATA_CLOSE = Pattern.compile("]]>");
	private static final Pattern TAG = Pattern.compile("<[^>]+>");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Map<String, String> HTML_ENTITIES = Map.of(
			"&nbsp;", " ",
			"&lt;", "<",
			"&gt;", ">",
			"&amp;", "&",
			"&quot;", "\"",
			"&#39;", "'"
	);

	private NbDocDataCleaner() {
	}

	public static String clean(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}

		String cleaned = CDATA_OPEN.matcher(raw).replaceAll("");
		cleaned = CDATA_CLOSE.matcher(cleaned).replaceAll("");
		for (Map.Entry<String, String> entry : HTML_ENTITIES.entrySet()) {
			cleaned = cleaned.replace(entry.getKey(), entry.getValue());
		}
		cleaned = TAG.matcher(cleaned).replaceAll(" ");
		return WHITESPACE.matcher(cleaned).replaceAll(" ").strip();
	}
}
