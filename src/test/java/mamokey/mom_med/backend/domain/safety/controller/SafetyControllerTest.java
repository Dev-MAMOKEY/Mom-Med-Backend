package mamokey.mom_med.backend.domain.safety.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import mamokey.mom_med.backend.domain.safety.model.SafetyDecision;
import mamokey.mom_med.backend.domain.safety.model.SafetyEvidence;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import mamokey.mom_med.backend.domain.safety.service.SafetyCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SafetyController가 판정 결과를 약속된 HTTP 상태코드와 JSON 구조로 반환하는지 확인합니다.
 *
 * <p>서비스 내부 DUR 조회는 SafetyJudgeServiceTest에서 검증하고, 여기서는 BLOCK=409,
 * WARN/ALLOW=200이라는 프론트 계약을 집중적으로 검증합니다.</p>
 */
@WebMvcTest(SafetyController.class)
class SafetyControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	SafetyCheckService safetyCheckService;

	@Test
	void blockVerdictReturnsHttp409() throws Exception {
		when(safetyCheckService.check(any())).thenReturn(new SafetyVerdict(
				SafetyDecision.BLOCK,
				List.of(new SafetyEvidence(
						"DUR",
						"병용금기",
						"amlodipine",
						"itraconazole",
						"병용금기 fixture",
						"combo-1",
						LocalDate.of(2025, 6, 1)
				))
		));

		mockMvc.perform(post("/v1/safety/check")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "parent_id": "parent-1",
								  "age": 72,
								  "current_drugs": ["200610660"],
								  "new_drug": "200502107"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("block"))
				.andExpect(jsonPath("$.verdict.decision").value("BLOCK"))
				.andExpect(jsonPath("$.verdict.evidences", hasSize(1)))
				.andExpect(jsonPath("$.verdict.evidences[0].ingredient_a").value("amlodipine"))
				.andExpect(jsonPath("$.verdict.evidences[0].ingredient_b").value("itraconazole"));
	}

	@Test
	void allowVerdictReturnsHttp200() throws Exception {
		when(safetyCheckService.check(any())).thenReturn(new SafetyVerdict(SafetyDecision.ALLOW, List.of()));

		mockMvc.perform(post("/v1/safety/check")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "parent_id": "parent-1",
								  "age": 60,
								  "current_drugs": ["warfarin-item"],
								  "new_drug": "aspirin-item"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.decision").value("ALLOW"))
				.andExpect(jsonPath("$.evidences", hasSize(0)));
	}

	@Test
	void warnVerdictReturnsHttp200() throws Exception {
		when(safetyCheckService.check(any())).thenReturn(new SafetyVerdict(
				SafetyDecision.WARN,
				List.of(new SafetyEvidence(
						"DUR",
						"노인주의",
						"zolpidem",
						null,
						"고령자 주의 필요",
						"elderly-1",
						LocalDate.of(2025, 6, 1)
				))
		));

		mockMvc.perform(post("/v1/safety/check")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "parent_id": "parent-1",
								  "age": 72,
								  "current_drugs": ["acetaminophen-item"],
								  "new_drug": "zolpidem-item"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.decision").value("WARN"))
				.andExpect(jsonPath("$.evidences[0].type").value("노인주의"));
	}
}
