package mamokey.mom_med.backend.domain.nb.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 이미 검증된 NB 추출 골든 fixture를 보호하는 테스트입니다.
 *
 * <p>이 테스트는 Gemini를 호출하지 않습니다. 대신 사람이 확인해 둔 JSON 결과를 읽어
 * "노바스크 39건, 와파린 46건"이라는 Slice 03 기준선과 핵심 상호작용 누락 여부를 빠르게 확인합니다.</p>
 */
class NbGoldenFixtureTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Test
	void amlodipineGoldenFixtureKeepsExpectedInteractions() throws IOException {
		Map<String, Object> fixture = readFixture("nb_amlodipine_extracted.json");
		List<Map<String, Object>> interactions = interactions(fixture);

		assertThat(interactions).hasSize(39);
		assertThat(koreanPartners(interactions)).contains(
				"심바스타틴",
				"이트라코나졸",
				"케토코나졸",
				"시클로스포린",
				"클래리트로마이신",
				"단트롤렌",
				"자몽"
		);
		assertThat(englishPartners(interactions)).contains(
				"simvastatin",
				"itraconazole",
				"ketoconazole",
				"cyclosporine",
				"clarithromycin",
				"dantrolene"
		);
	}

	@Test
	void warfarinGoldenFixtureKeepsExpectedInteractions() throws IOException {
		Map<String, Object> fixture = readFixture("nb_warfarin_extracted.json");
		List<Map<String, Object>> interactions = interactions(fixture);

		assertThat(interactions).hasSize(46);
		assertThat(koreanPartners(interactions)).contains("아스피린", "비스테로이드소염제", "비타민 K");
		assertThat(englishPartners(interactions)).contains("aspirin", "NSAIDs", "itraconazole", "vitamin K");
	}

	private static Map<String, Object> readFixture(String fileName) throws IOException {
		return OBJECT_MAPPER.readValue(Path.of(fileName).toFile(), new TypeReference<>() {
		});
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> interactions(Map<String, Object> fixture) {
		return (List<Map<String, Object>>) fixture.get("interactions");
	}

	private static List<String> koreanPartners(List<Map<String, Object>> interactions) {
		return interactions.stream()
				.map(row -> row.get("partner_drug_ko"))
				.filter(Objects::nonNull)
				.map(Object::toString)
				.collect(Collectors.toList());
	}

	private static List<String> englishPartners(List<Map<String, Object>> interactions) {
		return interactions.stream()
				.map(row -> row.get("partner_drug_en"))
				.filter(Objects::nonNull)
				.map(Object::toString)
				.collect(Collectors.toList());
	}
}
