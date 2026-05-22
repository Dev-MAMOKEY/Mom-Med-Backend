package mamokey.mom_med.backend.infra.llm;

/**
 * LLM 호출 결과를 후속 슬라이스가 공통 형식으로 다루기 위한 응답 record입니다.
 *
 * <p>GeminiClient는 Google API의 원본 응답 구조를 이 record로 변환합니다.
 * Slice 03 같은 비즈니스 로직은 특정 LLM 제공사의 응답 JSON을 직접 알 필요 없이
 * text, token, model, prompt version만 읽으면 됩니다.</p>
 *
 * @param text LLM이 생성한 최종 텍스트입니다. JSON mode에서는 JSON 문자열이 들어옵니다.
 * @param inputTokens prompt에 사용된 token 수입니다.
 * @param outputTokens 응답 생성에 사용된 token 수입니다.
 * @param costUsd 비용 추적용 값입니다. 현재는 pricing 모듈이 없어 0.0으로 채웁니다.
 * @param model 호출한 LLM 모델명입니다.
 * @param promptVersion prompt/schema 변경 이력을 추적하기 위한 버전입니다.
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
