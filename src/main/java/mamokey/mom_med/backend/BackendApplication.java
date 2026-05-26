package mamokey.mom_med.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mom Med 백엔드 애플리케이션의 진입점입니다.
 *
 * <p>@SpringBootApplication이 이 패키지 하위의 Component, Configuration, Controller 등을 스캔합니다.
 * 따라서 새 패키지를 만들 때는 mamokey.mom_med.backend 아래에 두는 것이 기본 규칙입니다.</p>
 */
@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		// Spring Boot 컨테이너를 시작하고 application.yml 설정을 읽어 애플리케이션을 구동합니다.
		SpringApplication.run(BackendApplication.class, args);
	}

}
