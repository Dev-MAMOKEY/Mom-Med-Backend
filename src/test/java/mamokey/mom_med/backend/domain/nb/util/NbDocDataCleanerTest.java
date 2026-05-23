package mamokey.mom_med.backend.domain.nb.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 식약처 NB_DOC_DATA 원문 전처리 유틸의 회귀 테스트입니다.
 *
 * <p>Slice 03의 HallucinationVerifier는 LLM이 반환한 source_quote를 이 정제된 평문에서 찾습니다.
 * 따라서 CDATA, 태그, HTML entity를 제거하되 원문 문장의 순서와 의미는 유지되어야 합니다.</p>
 */
class NbDocDataCleanerTest {

	@Test
	void removesMarkupAndNormalizesWhitespaceForQuoteVerification() {
		String raw = """
				<DOC><![CDATA[
				  <p>암로디핀&nbsp;10 mg과 <b>심바스타틴</b> 병용 시 주의한다.</p>
				  <p>source_quote 검증을 위해 문장은 남겨야 한다.</p>
				]]></DOC>
				""";

		String cleaned = NbDocDataCleaner.clean(raw);

		assertThat(cleaned)
				.doesNotContain("<DOC>", "<p>", "<b>", "CDATA", "&nbsp;")
				.contains("암로디핀 10 mg과 심바스타틴 병용 시 주의한다.")
				.contains("source_quote 검증을 위해 문장은 남겨야 한다.");
	}
}
