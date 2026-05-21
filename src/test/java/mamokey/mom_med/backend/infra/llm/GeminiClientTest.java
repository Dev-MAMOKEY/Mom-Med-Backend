package mamokey.mom_med.backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiClientTest {

	@Test
	void parsesGeminiResponse() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl("https://generativelanguage.googleapis.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiClient client = new GeminiClient(builder.build(), "test-key", "gemini-2.5-flash-lite", "v1",
				Duration.ZERO);

		server.expect(once(), requestTo(
						"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
				))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-goog-api-key", "test-key"))
				.andRespond(withSuccess("""
						{
						  "candidates": [
						    {
						      "content": {
						        "parts": [
						          { "text": "{\\"ok\\":true}" }
						        ]
						      }
						    }
						  ],
						  "usageMetadata": {
						    "promptTokenCount": 3,
						    "candidatesTokenCount": 5
						  }
						}
						""", MediaType.APPLICATION_JSON));

		LLMResponse response = client.call("ping");

		assertThat(response.text()).isEqualTo("{\"ok\":true}");
		assertThat(response.inputTokens()).isEqualTo(3);
		assertThat(response.outputTokens()).isEqualTo(5);
		assertThat(response.costUsd()).isZero();
		assertThat(response.model()).isEqualTo("gemini-2.5-flash-lite");
		assertThat(response.promptVersion()).isEqualTo("v1");
		server.verify();
	}

	@Test
	void retriesOnceOnServerError() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl("https://generativelanguage.googleapis.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiClient client = new GeminiClient(builder.build(), "test-key", "gemini-2.5-flash-lite", "v1",
				Duration.ZERO);

		server.expect(once(), requestTo(
						"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
				))
				.andRespond(withServerError());
		server.expect(once(), requestTo(
						"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
				))
				.andRespond(withSuccess("""
						{
						  "candidates": [
						    {
						      "content": {
						        "parts": [
						          { "text": "{\\"retry\\":true}" }
						        ]
						      }
						    }
						  ],
						  "usageMetadata": {
						    "promptTokenCount": 1,
						    "candidatesTokenCount": 2
						  }
						}
						""", MediaType.APPLICATION_JSON));

		LLMResponse response = client.call("ping");

		assertThat(response.text()).isEqualTo("{\"retry\":true}");
		server.verify();
	}
}
