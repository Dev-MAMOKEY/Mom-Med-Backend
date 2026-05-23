package mamokey.mom_med.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing(@CreatedDate, @LastModifiedDate)을 활성화하는 설정입니다.
 *
 * <p>@SpringBootApplication과 분리된 @Configuration으로 선언해
 * @WebMvcTest 슬라이스 테스트에서 JPA 컨텍스트 없이 로드해도 충돌이 없도록 합니다.</p>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingAutoConfig {
}
