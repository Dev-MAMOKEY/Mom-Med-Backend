package mamokey.mom_med.backend.infra.llm;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini API 호출을 담당하는 LLM 클라이언트입니다.
 *
 * <p>후속 슬라이스가 Google API 요청/응답 구조를 직접 알 필요 없이 {@link #call(String)}만 사용하게 하는
 * 얇은 래퍼입니다. API key, 기본 모델, prompt version은 application-local.yml의 external.gemini 설정에서
 * 주입됩니다.</p>
 *
 * <p>유지보수 주의: 이 클래스는 "HTTP 호출과 응답 파싱"까지만 책임집니다.
 * NB 추출 prompt 작성, 결과 schema 검증, 환각 검증은 Slice 03 비즈니스 로직에서 처리해야 합니다.</p>
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

	@Autowired
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
		// package-private 생성자는 MockRestServiceServer를 붙이는 단위 테스트에서만 사용합니다.
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.defaultModel = defaultModel;
		this.defaultPromptVersion = defaultPromptVersion;
		this.retryBackoff = retryBackoff;
	}

	/**
	 * 기본 모델, JSON mode, 기본 prompt version으로 Gemini를 호출합니다.
	 */
	public LLMResponse call(String prompt) {
		return call(prompt, defaultModel, true, DEFAULT_MAX_OUTPUT_TOKENS, defaultPromptVersion);
	}

	/**
	 * Slice 03 NB 추출용 JSON mode 호출입니다.
	 *
	 * <p>NB 추출은 회귀 재현성이 중요하므로 temperature를 낮게 고정합니다.
	 * 일반 call 메서드는 기존 Slice 00 호환성을 위해 temperature를 보내지 않습니다.</p>
	 */
	public LLMResponse callJsonExtraction(String prompt, String promptVersion) {
		return call(prompt, defaultModel, true, DEFAULT_MAX_OUTPUT_TOKENS, promptVersion, 0.1);
	}

	/**
	 * Gemini generateContent API를 호출하고 프로젝트 공통 응답 형식으로 변환합니다.
	 *
	 * <p>요청 body는 Google API 규격에 맞춰 contents와 generationConfig로 구성합니다.
	 * jsonMode가 true이면 responseMimeType을 application/json으로 보내 후속 JSON 파싱 안정성을 높입니다.</p>
	 */
	public LLMResponse call(
			String prompt,
			String model,
			boolean jsonMode,
			int maxOutputTokens,
			String promptVersion
	) {
		return call(prompt, model, jsonMode, maxOutputTokens, promptVersion, null);
	}

	public LLMResponse call(
			String prompt,
			String model,
			boolean jsonMode,
			int maxOutputTokens,
			String promptVersion,
			Double temperature
	) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new GeminiClientException("Gemini API key is required. Set GEMINI_API_KEY.");
		}
		if (prompt == null || prompt.isBlank()) {
			throw new GeminiClientException("Gemini prompt must not be blank.");
		}

		Map<String, Object> generationConfig = new LinkedHashMap<>();
		generationConfig.put("responseMimeType", jsonMode ? "application/json" : "text/plain");
		generationConfig.put("maxOutputTokens", maxOutputTokens);
		if (temperature != null) {
			generationConfig.put("temperature", temperature);
		}

		Map<String, Object> request = Map.of(
				"contents", List.of(Map.of(
						"parts", List.of(Map.of("text", prompt))
				)),
				"generationConfig", generationConfig
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
				// Gemini 서버의 일시적 5xx 오류는 한 번만 재시도합니다. 4xx는 요청 문제이므로 바로 실패시킵니다.
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

	/**
	 * 외부 LLM 호출 실패를 애플리케이션 내부 예외로 감싸기 위한 예외 타입입니다.
	 */
	public static class GeminiClientException extends RuntimeException {

		public GeminiClientException(String message) {
			super(message);
		}

		public GeminiClientException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	// 아래 record들은 Gemini 응답 JSON에서 현재 필요한 필드만 파싱하기 위한 내부 DTO입니다.
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
