package mamokey.mom_med.backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * 실제 Gemini API key가 있을 때만 실행되는 ping 통합 테스트입니다.
 *
 * <p>외부 네트워크와 과금 가능성이 있는 테스트이므로 기본 CI에서는 실행하지 않습니다.
 * 로컬에서 GEMINI_API_KEY 환경변수를 넣고 실행하면 GeminiClient.call("ping")이 실제 응답을 받는지 확인합니다.</p>
 */
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
