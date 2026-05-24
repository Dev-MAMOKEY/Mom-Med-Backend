package mamokey.mom_med.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 전역에서 사용할 Jackson ObjectMapper 설정입니다.
 *
 * <p>Slice 03의 {@code NbExtractionService}는 Gemini가 반환한 JSON 문자열을
 * {@code Map<String, Object>}로 파싱해야 하므로 ObjectMapper Bean이 필요합니다.
 * 현재 프로젝트 구성에서는 ObjectMapper가 자동 Bean으로 등록되지 않는 환경이 있어,
 * 여기서 명시적으로 등록해 ApplicationContext 시작 실패를 막습니다.</p>
 *
 * <p>{@link ConditionalOnMissingBean}을 붙여 두면 이후 Spring Boot Jackson starter나
 * 별도 커스텀 ObjectMapper가 추가되더라도 이 설정이 기존 Bean을 덮어쓰지 않습니다.</p>
 */
@Configuration
public class JacksonConfig {

	@Bean
	@ConditionalOnMissingBean(ObjectMapper.class)
	public ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}
}
