package mamokey.mom_med.backend.infra.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini API 호출을 감싸는 얇은 클라이언트입니다.
 * <p>
 * 후속 슬라이스는 Google API 요청/응답 구조를 직접 알 필요 없이 {@link #call(String)}만 사용합니다.
 */
@Component
public class GeminiClient {

	private static final String API_BASE_URL = "https://generativelanguage.googleapis.com";
	private static final int DEFAULT_MAX_OUTPUT_TOKENS = 8192;

	private final RestClient restClient;
	private final String apiKey;
	private final String defaultModel;
	private final String defaultPromptVersion;
	private final Duration retryBackoff;

	public GeminiClient(
			RestClient.Builder restClientBuilder,
			@Value("${external.gemini.api-key:}") String apiKey,
			@Value("${external.gemini.model:gemini-2.5-flash-lite}") String defaultModel,
			@Value("${external.gemini.prompt-version:v1}") String defaultPromptVersion
	) {
		this(restClientBuilder.baseUrl(API_BASE_URL).build(), apiKey, defaultModel, defaultPromptVersion,
				Duration.ofSeconds(1));
	}

	GeminiClient(
			RestClient restClient,
			String apiKey,
			String defaultModel,
			String defaultPromptVersion,
			Duration retryBackoff
	) {
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.defaultModel = defaultModel;
		this.defaultPromptVersion = defaultPromptVersion;
		this.retryBackoff = retryBackoff;
	}

	public LLMResponse call(String prompt) {
		return call(prompt, defaultModel, true, DEFAULT_MAX_OUTPUT_TOKENS, defaultPromptVersion);
	}

	public LLMResponse call(
			String prompt,
			String model,
			boolean jsonMode,
			int maxOutputTokens,
			String promptVersion
	) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new GeminiClientException("Gemini API key is required. Set GEMINI_API_KEY.");
		}
		if (prompt == null || prompt.isBlank()) {
			throw new GeminiClientException("Gemini prompt must not be blank.");
		}

		Map<String, Object> request = Map.of(
				"contents", List.of(Map.of(
						"parts", List.of(Map.of("text", prompt))
				)),
				"generationConfig", Map.of(
						"responseMimeType", jsonMode ? "application/json" : "text/plain",
						"maxOutputTokens", maxOutputTokens
				)
		);

		GeminiGenerateContentResponse response = executeWithSingleRetry(model, request);
		String text = extractText(response);
		GeminiUsageMetadata usage = response.usageMetadata();

		// 실제 과금 단가는 모델/시점에 따라 바뀌므로 pricing 모듈이 생기기 전까지 0으로 고정합니다.
		return new LLMResponse(
				text,
				usage == null ? 0 : usage.promptTokenCount(),
				usage == null ? 0 : usage.candidatesTokenCount(),
				0.0,
				model,
				promptVersion
		);
	}

	private GeminiGenerateContentResponse executeWithSingleRetry(String model, Map<String, Object> request) {
		for (int attempt = 0; attempt < 2; attempt++) {
			try {
				return restClient.post()
						.uri("/v1beta/models/{model}:generateContent", model)
						.header("x-goog-api-key", apiKey)
						.contentType(MediaType.APPLICATION_JSON)
						.body(request)
						.retrieve()
						.body(GeminiGenerateContentResponse.class);
			}
			catch (RestClientResponseException exception) {
				if (exception.getStatusCode().is5xxServerError() && attempt == 0) {
					sleepBeforeRetry();
					continue;
				}
				throw new GeminiClientException("Gemini API request failed: " + exception.getStatusCode(), exception);
			}
			catch (RestClientException exception) {
				throw new GeminiClientException("Gemini API request failed.", exception);
			}
		}

		throw new GeminiClientException("Gemini API request failed after retry.");
	}

	private void sleepBeforeRetry() {
		if (retryBackoff.isZero() || retryBackoff.isNegative()) {
			return;
		}
		try {
			Thread.sleep(retryBackoff.toMillis());
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new GeminiClientException("Interrupted while waiting to retry Gemini API request.", exception);
		}
	}

	private String extractText(GeminiGenerateContentResponse response) {
		if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
			throw new GeminiClientException("Gemini API response did not include candidates.");
		}

		GeminiContent content = response.candidates().getFirst().content();
		if (content == null || content.parts() == null || content.parts().isEmpty()) {
			throw new GeminiClientException("Gemini API response did not include content parts.");
		}

		String text = content.parts().getFirst().text();
		if (text == null || text.isBlank()) {
			throw new GeminiClientException("Gemini API response text was blank.");
		}
		return text;
	}

	public static class GeminiClientException extends RuntimeException {

		public GeminiClientException(String message) {
			super(message);
		}

		public GeminiClientException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private record GeminiGenerateContentResponse(
			List<GeminiCandidate> candidates,
			GeminiUsageMetadata usageMetadata
	) {
	}

	private record GeminiCandidate(GeminiContent content) {
	}

	private record GeminiContent(List<GeminiPart> parts) {
	}

	private record GeminiPart(String text) {
	}

	private record GeminiUsageMetadata(int promptTokenCount, int candidatesTokenCount) {
	}
}
