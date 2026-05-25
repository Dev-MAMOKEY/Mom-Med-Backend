package mamokey.mom_med.backend.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import mamokey.mom_med.backend.domain.weather.entity.WeatherRule;
import mamokey.mom_med.backend.domain.weather.model.WeatherAdvisory;
import mamokey.mom_med.backend.domain.weather.repository.WeatherRuleRepository;
import mamokey.mom_med.backend.parent.domain.PatientCondition;
import mamokey.mom_med.backend.parent.repository.PatientConditionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 질환 코드와 기상특보가 정확 매칭으로 advisory로 변환되는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class WeatherDiseaseAdvisorTest {

	@Mock
	WeatherRuleRepository weatherRuleRepository;

	@Mock
	PatientConditionRepository patientConditionRepository;

	@Test
	void lookupRulesReturnsAdvisoriesWithRuleId() {
		WeatherRule hypertension = rule(5L, "I10", "본태성 고혈압", "폭염경보", "위험", false);
		WeatherRule diabetes = rule(6L, "E11", "2형 당뇨병", "폭염경보", "경고", true);
		when(weatherRuleRepository.findByDiseaseCodeInAndWeatherAlertIn(
				List.of("I10", "E11"),
				List.of("폭염경보")
		)).thenReturn(List.of(hypertension, diabetes));

		WeatherDiseaseAdvisor advisor = new WeatherDiseaseAdvisor(weatherRuleRepository, patientConditionRepository);
		List<WeatherAdvisory> advisories = advisor.lookupRules(List.of("I10", "E11"), List.of("폭염경보"));

		assertThat(advisories).hasSize(2);
		assertThat(advisories).extracting(WeatherAdvisory::ruleId).containsExactly(5L, 6L);
		assertThat(advisories).extracting(WeatherAdvisory::diseaseCode).containsExactly("I10", "E11");
		assertThat(advisories.get(1).requiresReview()).isTrue();
	}

	@Test
	void lookupRulesReturnsEmptyWhenAnyInputIsEmpty() {
		WeatherDiseaseAdvisor advisor = new WeatherDiseaseAdvisor(weatherRuleRepository, patientConditionRepository);

		assertThat(advisor.lookupRules(List.of(), List.of("폭염경보"))).isEmpty();
		assertThat(advisor.lookupRules(List.of("I10"), List.of())).isEmpty();
	}

	@Test
	void getParentConditionsReturnsActiveKcdCodesOnly() {
		UUID parentId = UUID.randomUUID();
		when(patientConditionRepository.findByParentIdAndDeletedAtIsNull(parentId)).thenReturn(List.of(
				PatientCondition.create(parentId, "고혈압", "고혈압", "I10", "moderate", null, null),
				PatientCondition.create(parentId, "당뇨", "당뇨", "E11", "moderate", null, null),
				PatientCondition.create(parentId, "코드 없음", "코드 없음", null, "mild", null, null)
		));

		WeatherDiseaseAdvisor advisor = new WeatherDiseaseAdvisor(weatherRuleRepository, patientConditionRepository);

		assertThat(advisor.getParentConditions(parentId)).containsExactly("I10", "E11");
	}

	private static WeatherRule rule(Long id, String diseaseCode, String diseaseName, String alert, String severity,
			boolean generalKnowledgeUsed) {
		WeatherRule rule = WeatherRule.create(
				"v0.2",
				diseaseCode,
				diseaseName,
				alert,
				severity,
				"title",
				"message",
				List.of("이뇨제"),
				List.of("수분 섭취"),
				List.of(Map.of("file", "seed", "quote", "quote")),
				"rationale",
				generalKnowledgeUsed
		);
		ReflectionTestUtils.setField(rule, "id", id);
		return rule;
	}
}
