package mamokey.mom_med.backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

class GeminiClientIntegrationTest {

	@Test
	@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
	void callPingWhenApiKeyExists() {
		GeminiClient client = new GeminiClient(
				RestClient.builder().baseUrl("https://generativelanguage.googleapis.com").build(),
				System.getenv("GEMINI_API_KEY"),
				System.getenv().getOrDefault("GEMINI_MODEL", "gemini-2.5-flash-lite"),
				"v1",
				java.time.Duration.ofSeconds(1)
		);

		LLMResponse response = client.call("ping");

		assertThat(response.text()).isNotBlank();
	}
}
