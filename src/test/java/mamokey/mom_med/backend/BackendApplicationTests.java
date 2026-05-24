package mamokey.mom_med.backend;

import mamokey.mom_med.backend.domain.drug.repository.DrugMasterRepository;
import mamokey.mom_med.backend.domain.drug.repository.PillVisualRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurAgeContraindicationRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurComboContraindicationRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurElderlyCautionRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurElderlyNsaidCautionRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurPregnancyContraindicationRepository;
import mamokey.mom_med.backend.domain.nb.repository.NbExtractionRepository;
import mamokey.mom_med.backend.domain.nb.repository.NbInteractionRepository;
import mamokey.mom_med.backend.external.hira.HiraClient;
import mamokey.mom_med.backend.external.mfds.MfdsClient;
import mamokey.mom_med.backend.parent.repository.PatientAllergyRepository;
import mamokey.mom_med.backend.parent.repository.PatientConditionRepository;
import mamokey.mom_med.backend.parent.repository.DeviceTokenRepository;
import mamokey.mom_med.backend.parent.repository.PatientMedicationRepository;
import mamokey.mom_med.backend.parent.repository.PatientProfileRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
				+ "org.redisson.spring.starter.RedissonAutoConfigurationV4",
		// DurCsvLoadRunner가 @ConditionalOnProperty로 활성화되지 않도록 비활성화합니다.
		"app.etl.dur.enabled=false"
})
class BackendApplicationTests {

	@MockitoBean
	DrugMasterRepository drugMasterRepository;

	@MockitoBean
	PillVisualRepository pillVisualRepository;

	@MockitoBean
	DurComboContraindicationRepository durComboContraindicationRepository;

	@MockitoBean
	DurElderlyCautionRepository durElderlyCautionRepository;

	@MockitoBean
	DurElderlyNsaidCautionRepository durElderlyNsaidCautionRepository;

	@MockitoBean
	DurAgeContraindicationRepository durAgeContraindicationRepository;

	@MockitoBean
	DurPregnancyContraindicationRepository durPregnancyContraindicationRepository;

	@MockitoBean
	NbExtractionRepository nbExtractionRepository;

	@MockitoBean
	NbInteractionRepository nbInteractionRepository;

	@MockitoBean
	PatientProfileRepository patientProfileRepository;

	@MockitoBean
	PatientAllergyRepository patientAllergyRepository;

	@MockitoBean
	PatientConditionRepository patientConditionRepository;

	@MockitoBean
	PatientMedicationRepository patientMedicationRepository;

	@MockitoBean
	DeviceTokenRepository deviceTokenRepository;

	@MockitoBean
	MfdsClient mfdsClient;

	@MockitoBean
	HiraClient hiraClient;

	@MockitoBean
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	@MockitoBean
	ObjectMapper objectMapper;

	@Test
	void contextLoads() {
		// 컨텍스트가 뜨지 않으면 이 테스트는 메서드 본문 실행 전에 실패합니다.
	}

}
