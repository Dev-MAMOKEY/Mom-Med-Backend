package mamokey.mom_med.backend.domain.safety.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.dur.entity.DurComboContraindication;
import mamokey.mom_med.backend.domain.dur.entity.DurElderlyCaution;
import mamokey.mom_med.backend.domain.dur.repository.DurComboContraindicationRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurElderlyCautionRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurElderlyNsaidCautionRepository;
import mamokey.mom_med.backend.domain.dur.service.DurRuleEngine;
import mamokey.mom_med.backend.domain.nb.service.NbExtractionResult;
import mamokey.mom_med.backend.domain.nb.service.NbExtractionService;
import mamokey.mom_med.backend.domain.safety.model.SafetyDecision;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * DUR 기반 안전 판정 회귀 테스트입니다.
 *
 * <p>실제 HIRA CSV 전체를 테스트마다 적재하면 느리고 불안정하므로, 최소 fixture 행만 mock으로 반환합니다.
 * 중요한 것은 RuleEngine이 정규화 성분명으로 정확 조회한 결과를 SafetyJudge가 BLOCK/WARN/ALLOW로
 * 올바르게 조립하는지입니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SafetyJudgeServiceTest {

	private static final LocalDate GAZETTE_DATE = LocalDate.of(2025, 6, 1);
	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC);

	@Mock
	DurComboContraindicationRepository comboRepository;

	@Mock
	DurElderlyCautionRepository elderlyCautionRepository;

	@Mock
	DurElderlyNsaidCautionRepository elderlyNsaidCautionRepository;

	@Mock
	NbExtractionService nbExtractionService;

	SafetyJudgeService safetyJudgeService;

	@BeforeEach
	void setUp() {
		lenient().when(comboRepository.findExactPair(anyString(), anyString())).thenReturn(List.of());
		lenient().when(elderlyCautionRepository.findByIngredientNorm(anyString())).thenReturn(List.of());
		lenient().when(elderlyNsaidCautionRepository.findByIngredientNorm(anyString())).thenReturn(List.of());

		DurRuleEngine durRuleEngine = new DurRuleEngine(
				comboRepository,
				elderlyCautionRepository,
				elderlyNsaidCautionRepository
		);
		lenient().when(nbExtractionService.extract(anyString()))
				.thenReturn(new NbExtractionResult(null, List.of(), true));
		safetyJudgeService = new SafetyJudgeService(durRuleEngine, nbExtractionService);
	}

	@Test
	void amlodipineAndItraconazoleBlocksByDurCombination() {
		when(comboRepository.findExactPair("amlodipine", "itraconazole")).thenReturn(List.of(
				combo(1L, "amlodipine", "itraconazole")
		));

		SafetyVerdict verdict = safetyJudgeService.judge(
				List.of(drug("200610660", "amlodipine")),
				drug("200502107", "itraconazole"),
				72
		);

		assertThat(verdict.decision()).isEqualTo(SafetyDecision.BLOCK);
		assertThat(verdict.evidences()).hasSize(1);
		assertThat(verdict.evidences().getFirst().type()).isEqualTo("병용금기");
	}

	@Test
	void fluorouracilAndTegafurBlocksByDurCombination() {
		when(comboRepository.findExactPair("5-fluorouracil", "tegafur")).thenReturn(List.of(
				combo(2L, "5-fluorouracil", "tegafur")
		));

		SafetyVerdict verdict = safetyJudgeService.judge(
				List.of(drug("fluorouracil-item", "5-fluorouracil")),
				drug("tegafur-item", "tegafur"),
				60
		);

		assertThat(verdict.decision()).isEqualTo(SafetyDecision.BLOCK);
	}

	@Test
	void warfarinAndAspirinAllowsWhenNoDurEvidenceExists() {
		SafetyVerdict verdict = safetyJudgeService.judge(
				List.of(drug("warfarin-item", "warfarin")),
				drug("aspirin-item", "aspirin"),
				60
		);

		assertThat(verdict.decision()).isEqualTo(SafetyDecision.ALLOW);
		assertThat(verdict.evidences()).isEmpty();
	}

	@Test
	void amlodipineAndSimvastatinAllowsInDurMvp() {
		SafetyVerdict verdict = safetyJudgeService.judge(
				List.of(drug("amlodipine-item", "amlodipine")),
				drug("simvastatin-item", "simvastatin"),
				72
		);

		assertThat(verdict.decision()).isEqualTo(SafetyDecision.ALLOW);
	}

	@Test
	void elderlyCautionWarnsForAgeOver65() {
		when(elderlyCautionRepository.findByIngredientNorm("zolpidem")).thenReturn(List.of(
				DurElderlyCaution.fixture(1L, "zolpidem", "고령자 주의 필요", "elderly-1", GAZETTE_DATE)
		));

		SafetyVerdict verdict = safetyJudgeService.judge(
				List.of(drug("current-item", "acetaminophen")),
				drug("zolpidem-item", "zolpidem"),
				72
		);

		assertThat(verdict.decision()).isEqualTo(SafetyDecision.WARN);
		assertThat(verdict.evidences()).hasSize(1);
		assertThat(verdict.evidences().getFirst().type()).isEqualTo("노인주의");
	}

	@Test
	void duplicateRawCombinationRowsAreDedupedToOneEvidence() {
		when(comboRepository.findExactPair("amlodipine", "itraconazole")).thenReturn(List.of(
				combo(1L, "amlodipine", "itraconazole"),
				combo(2L, "amlodipine", "itraconazole")
		));

		SafetyVerdict verdict = safetyJudgeService.judge(
				List.of(drug("amlodipine-item", "amlodipine")),
				drug("itraconazole-item", "itraconazole"),
				72
		);

		assertThat(verdict.decision()).isEqualTo(SafetyDecision.BLOCK);
		assertThat(verdict.evidences()).hasSize(1);
	}

	private static DurComboContraindication combo(Long id, String ingredientA, String ingredientB) {
		return DurComboContraindication.fixture(
				id,
				ingredientA,
				ingredientB,
				"병용금기 fixture",
				"combo-" + id,
				GAZETTE_DATE
		);
	}

	private static DrugMaster drug(String itemSeq, String ingredientNorm) {
		DrugMaster drug = DrugMaster.create(itemSeq);
		drug.refresh(new DrugMaster.DrugMasterRefreshValues(
				itemSeq + " name",
				null,
				"fixture company",
				null,
				null,
				"전문의약품",
				null,
				null,
				ingredientNorm,
				ingredientNorm,
				null,
				null,
				null,
				null,
				null
		), FIXED_CLOCK);
		return drug;
	}
}
