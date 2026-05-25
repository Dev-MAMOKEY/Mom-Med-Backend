package mamokey.mom_med.backend.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import mamokey.mom_med.backend.domain.weather.dto.WeatherRuleApproveRequest;
import mamokey.mom_med.backend.domain.weather.entity.WeatherRule;
import mamokey.mom_med.backend.domain.weather.repository.WeatherRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 의료진 검수 큐와 승인 플로우를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class WeatherRuleAdminServiceTest {

	@Mock
	WeatherRuleRepository weatherRuleRepository;

	@Test
	void needsReviewQueueReturnsOnlyUnapprovedGeneralKnowledgeRules() {
		WeatherRule rule = rule(17L, true);
		when(weatherRuleRepository.findByGeneralKnowledgeUsedTrueAndApprovedAtIsNullOrderByIdAsc())
				.thenReturn(List.of(rule));

		WeatherRuleAdminService service = new WeatherRuleAdminService(weatherRuleRepository);

		assertThat(service.getRules("needs_review").total()).isEqualTo(1);
		assertThat(service.getRules("needs_review").rules().getFirst().requiresReview()).isTrue();
	}

	@Test
	void approveFillsApprovedAtAndAdjustedMessage() {
		WeatherRule rule = rule(17L, true);
		when(weatherRuleRepository.findById(17L)).thenReturn(Optional.of(rule));

		WeatherRuleAdminService service = new WeatherRuleAdminService(weatherRuleRepository);
		var response = service.approve(17L, new WeatherRuleApproveRequest("약사 김OO", "보정 메시지"));

		assertThat(response.approvedBy()).isEqualTo("약사 김OO");
		assertThat(response.approvedAt()).isNotNull();
		assertThat(response.messageTemplate()).isEqualTo("보정 메시지");
		assertThat(response.requiresReview()).isFalse();
	}

	private static WeatherRule rule(Long id, boolean generalKnowledgeUsed) {
		WeatherRule rule = WeatherRule.create(
				"v0.2",
				"I10",
				"본태성 고혈압",
				"폭염경보",
				"위험",
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
