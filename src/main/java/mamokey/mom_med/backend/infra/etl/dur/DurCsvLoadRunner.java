package mamokey.mom_med.backend.infra.etl.dur;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DUR CSV 적재를 명령형으로 실행하는 Runner입니다.
 *
 * <p>DUR 병용금기 CSV는 매우 크기 때문에 일반 애플리케이션 기동 때마다 읽으면 느리고 위험합니다.
 * 그래서 {@code --app.etl.dur.enabled=true} 옵션이 있을 때만 실행되도록 분리했습니다.</p>
 */
@Component
@ConditionalOnProperty(name = "app.etl.dur.enabled", havingValue = "true")
public class DurCsvLoadRunner implements ApplicationRunner {

	private final DurCsvLoader durCsvLoader;

	public DurCsvLoadRunner(DurCsvLoader durCsvLoader) {
		this.durCsvLoader = durCsvLoader;
	}

	@Override
	public void run(ApplicationArguments args) {
		durCsvLoader.loadDefaults();
	}
}
