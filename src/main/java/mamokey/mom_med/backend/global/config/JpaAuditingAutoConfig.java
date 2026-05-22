package mamokey.mom_med.backend.global.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing(@CreatedDate, @LastModifiedDate)을 활성화하는 Auto-Configuration입니다.
 *
 * <p>Auto-Configuration으로 등록하는 이유:
 * 일반 @Configuration은 컴포넌트 스캔 시점에 평가되어, auto-configure 단계에서 생성되는
 * EntityManagerFactory를 @ConditionalOnBean으로 참조할 수 없습니다.
 * Auto-Configuration은 auto-configure 단계에서 평가되므로 @ConditionalOnBean이 올바르게 작동합니다.</p>
 *
 * <p>테스트에서 JPA를 제외할 때 이 설정도 함께 제외해야 합니다:
 * {@code spring.autoconfigure.exclude=...,mamokey.mom_med.backend.global.config.JpaAuditingAutoConfig}</p>
 */
@AutoConfiguration
@ConditionalOnBean(EntityManagerFactory.class)
@EnableJpaAuditing
public class JpaAuditingAutoConfig {
}
