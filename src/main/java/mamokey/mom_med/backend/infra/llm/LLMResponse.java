package mamokey.mom_med.backend.infra.llm;

/**
 * LLM 호출 결과를 후속 슬라이스가 공통으로 소비하기 위한 응답 record입니다.
 */
public record LLMResponse(
		String text,
		int inputTokens,
		int outputTokens,
		double costUsd,
		String model,
		String promptVersion
) {
}
