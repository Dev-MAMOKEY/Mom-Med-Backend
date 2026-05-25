package mamokey.mom_med.backend.infra.etl.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import mamokey.mom_med.backend.domain.weather.repository.WeatherRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * weather_rules_v0.2.json 형식의 seed가 ref.weather_rules 엔티티로 변환되는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class WeatherRuleLoaderTest {

	@Mock
	WeatherRuleRepository weatherRuleRepository;

	@TempDir
	Path tempDir;

	@Test
	void loadWeatherRulesFromJsonSeed() throws Exception {
		Path seed = tempDir.resolve("weather_rules.json");
		Files.writeString(seed, """
				{
				  "rules": [
				    {
				      "disease_code": "I10",
				      "disease_name": "본태성 고혈압",
				      "weather_alert": "폭염경보",
				      "severity": "위험",
				      "title": "폭염경보",
				      "message_template": "수분 섭취",
				      "specific_drugs_to_note": ["이뇨제"],
				      "patient_actions": ["외출 금지"],
				      "source_citations": [{"file": "seed", "quote": "quote"}],
				      "rationale": "폭염 위험",
				      "general_knowledge_used": true
				    }
				  ]
				}
				""");
		when(weatherRuleRepository.findByRuleVersionAndDiseaseCodeAndWeatherAlert("v0.2", "I10", "폭염경보"))
				.thenReturn(Optional.empty());

		WeatherRuleLoader loader = new WeatherRuleLoader(weatherRuleRepository, new ObjectMapper());
		WeatherRuleLoadResult result = loader.load(seed, "v0.2");

		assertThat(result.inserted()).isEqualTo(1);
		assertThat(result.updated()).isZero();
		assertThat(result.total()).isEqualTo(1);
		verify(weatherRuleRepository).save(any());
	}
}
