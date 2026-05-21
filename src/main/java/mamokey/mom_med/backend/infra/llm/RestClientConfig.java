package mamokey.mom_med.backend.infra.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 외부 HTTP API 호출에 사용할 RestClient 공통 설정입니다.
 */
@Configuration
public class RestClientConfig {

	@Bean
	public RestClient.Builder restClientBuilder() {
		// GeminiClient 같은 외부 연동 컴포넌트가 각자 baseUrl, header를 덧붙여 사용할 기본 빌더입니다.
		return RestClient.builder();
	}
}
