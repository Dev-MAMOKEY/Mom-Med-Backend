package mamokey.mom_med.backend.infra.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 외부 HTTP API 호출에 사용할 RestClient 공통 설정입니다.
 *
 * <p>GeminiClient 같은 외부 연동 컴포넌트는 이 Builder를 주입받아 baseUrl, header, body 설정을 덧붙입니다.
 * Builder를 Bean으로 등록해두면 테스트와 운영 코드에서 같은 방식으로 HTTP 클라이언트를 만들 수 있습니다.</p>
 */
@Configuration
public class RestClientConfig {

	@Bean
	public RestClient.Builder restClientBuilder() {
		// 각 클라이언트가 자신에게 필요한 baseUrl과 header를 추가할 수 있도록 비어 있는 기본 Builder를 제공합니다.
		return RestClient.builder();
	}
}
