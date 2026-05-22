package mamokey.mom_med.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring ApplicationContext가 최소 설정으로 정상 생성되는지 확인하는 smoke test입니다.
 *
 * <p>이 테스트는 공통 Bean wiring 오류를 빠르게 잡는 목적입니다.
 * 실제 DB/Redis 연결 검증은 별도 smoke test에서 수행하므로, 여기서는 DataSource/JPA/Redisson 자동 설정을 제외합니다.</p>
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.redisson.spring.starter.RedissonAutoConfigurationV4"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
		// 컨텍스트가 뜨지 않으면 이 테스트는 메서드 본문 실행 전에 실패합니다.
	}

}
