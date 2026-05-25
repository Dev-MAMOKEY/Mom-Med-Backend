package mamokey.mom_med.backend.infra.etl.weather;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import mamokey.mom_med.backend.domain.weather.entity.WeatherRule;
import mamokey.mom_med.backend.domain.weather.repository.WeatherRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * seed/weather_rules_v0.2.json을 ref.weather_rules에 적재하는 ETL 서비스입니다.
 *
 * <p>UNIQUE(rule_version, disease_code, weather_alert) 기준으로 upsert합니다.
 * 같은 seed를 여러 번 실행해도 rule_id를 유지하므로 Slice 07의 dedup 키가 흔들리지 않습니다.</p>
 */
@Service
public class WeatherRuleLoader {

	private final WeatherRuleRepository weatherRuleRepository;
	private final ObjectMapper objectMapper;

	public WeatherRuleLoader(WeatherRuleRepository weatherRuleRepository, ObjectMapper objectMapper) {
		this.weatherRuleRepository = weatherRuleRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public WeatherRuleLoadResult load(Path seedPath, String ruleVersion) {
		Map<String, Object> root = readSeed(seedPath);
		List<Map<String, Object>> rows = rules(root);
		int inserted = 0;
		int updated = 0;

		for (Map<String, Object> row : rows) {
			String diseaseCode = text(row.get("disease_code"));
			String weatherAlert = text(row.get("weather_alert"));
			WeatherRule rule = weatherRuleRepository
					.findByRuleVersionAndDiseaseCodeAndWeatherAlert(ruleVersion, diseaseCode, weatherAlert)
					.orElse(null);

			if (rule == null) {
				rule = WeatherRule.create(
						ruleVersion,
						diseaseCode,
						text(row.get("disease_name")),
						weatherAlert,
						text(row.get("severity")),
						text(row.get("title")),
						text(row.get("message_template")),
						stringList(row.get("specific_drugs_to_note")),
						stringList(row.get("patient_actions")),
						objectList(row.get("source_citations")),
						text(row.get("rationale")),
						bool(row.get("general_knowledge_used"))
				);
				inserted++;
			}
			else {
				rule.refresh(
						text(row.get("severity")),
						text(row.get("title")),
						text(row.get("message_template")),
						stringList(row.get("specific_drugs_to_note")),
						stringList(row.get("patient_actions")),
						objectList(row.get("source_citations")),
						text(row.get("rationale")),
						bool(row.get("general_knowledge_used"))
				);
				updated++;
			}
			weatherRuleRepository.save(rule);
		}

		return new WeatherRuleLoadResult(inserted, updated, rows.size());
	}

	private Map<String, Object> readSeed(Path seedPath) {
		try {
			String seedJson = stripUtf8Bom(Files.readString(seedPath));
			return objectMapper.readValue(seedJson, new TypeReference<>() {
			});
		}
		catch (IOException exception) {
			throw new IllegalStateException("weather_rules seed 파일을 읽을 수 없습니다: " + seedPath, exception);
		}
	}

	/**
	 * 일부 Windows 편집기/엑셀 변환 도구는 UTF-8 JSON 앞에 BOM(U+FEFF)을 붙입니다.
	 * JSON 파서는 첫 글자로 '{'를 기대하므로 seed 적재 직전에 BOM만 제거합니다.
	 */
	private static String stripUtf8Bom(String text) {
		if (text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF') {
			return text.substring(1);
		}
		return text;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> rules(Map<String, Object> root) {
		Object rawRules = root.get("rules");
		if (!(rawRules instanceof List<?> list)) {
			return List.of();
		}
		return (List<Map<String, Object>>) (List<?>) list;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> objectList(Object value) {
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		return (List<Map<String, Object>>) (List<?>) list;
	}

	private static List<String> stringList(Object value) {
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		return list.stream()
				.map(WeatherRuleLoader::text)
				.filter(text -> text != null && !text.isBlank())
				.toList();
	}

	private static String text(Object value) {
		return value == null ? null : value.toString();
	}

	private static boolean bool(Object value) {
		return value instanceof Boolean bool && bool;
	}
}
