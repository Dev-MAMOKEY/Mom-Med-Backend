package mamokey.mom_med.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 프론트엔드 개발 환경(Expo web — http://localhost:8081, 19006 등)에서
 * 브라우저가 cross-origin XHR로 백엔드 API를 호출할 수 있게 허용한다.
 *
 * <p>운영 환경에서는 보안상 도메인을 좁혀야 하므로, 추후 별도 profile로 분리할 것.</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(@NonNull CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOriginPatterns(
						"http://localhost:*",
						"http://127.0.0.1:*"
				)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(true)
				.maxAge(3600);
	}
}
