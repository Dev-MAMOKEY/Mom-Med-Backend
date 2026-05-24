package mamokey.mom_med.backend.global.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing(@CreatedDate, @LastModifiedDate)을 활성화하는 설정입니다.
 *
 * <p>@SpringBootApplication과 분리된 @Configuration으로 선언해
 * @WebMvcTest 슬라이스 테스트에서 JPA 컨텍스트 없이 로드해도 충돌이 없도록 합니다.
 * {@code @ConditionalOnBean(EntityManagerFactory.class)}으로 JPA 컨텍스트가 없는 테스트에서
 * JpaMappingContext 빈 생성 실패를 방지합니다.</p>
 */
@Configuration
@EnableJpaAuditing
@ConditionalOnBean(EntityManagerFactory.class)
public class JpaAuditingAutoConfig {
}
