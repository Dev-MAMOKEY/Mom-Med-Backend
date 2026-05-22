package mamokey.mom_med.backend.domain.dur.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.dur.entity.DurComboContraindication;
import mamokey.mom_med.backend.domain.dur.entity.DurElderlyCaution;
import mamokey.mom_med.backend.domain.dur.entity.DurElderlyNsaidCaution;
import mamokey.mom_med.backend.domain.dur.repository.DurComboContraindicationRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurElderlyCautionRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurElderlyNsaidCautionRepository;
import mamokey.mom_med.backend.domain.safety.model.SafetyEvidence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HIRA DUR reference table을 조회해 약물 안전 근거를 만드는 1차 RuleEngine입니다.
 *
 * <p>이 클래스는 Slice 02의 핵심 규칙을 담당합니다. 모든 조회는 {@code main_ingr_norm}과
 * DUR 적재 테이블의 {@code ingredient_norm*} 컬럼 사이의 {@code =} 정확 매칭으로만 수행합니다.
 * LIKE 검색은 대용량 DUR 데이터에서 오탐과 성능 문제를 만들기 때문에 사용하지 않습니다.</p>
 */
@Service
@Transactional(readOnly = true)
public class DurRuleEngine {

	private static final int ELDERLY_AGE_THRESHOLD = 65;

	private final DurComboContraindicationRepository comboRepository;
	private final DurElderlyCautionRepository elderlyCautionRepository;
	private final DurElderlyNsaidCautionRepository elderlyNsaidCautionRepository;

	public DurRuleEngine(
			DurComboContraindicationRepository comboRepository,
			DurElderlyCautionRepository elderlyCautionRepository,
			DurElderlyNsaidCautionRepository elderlyNsaidCautionRepository
	) {
		this.comboRepository = comboRepository;
		this.elderlyCautionRepository = elderlyCautionRepository;
		this.elderlyNsaidCautionRepository = elderlyNsaidCautionRepository;
	}

	/**
	 * 두 약의 정규화 성분명으로 병용금기 약쌍을 검사합니다.
	 *
	 * <p>CSV에는 A→B 방향으로 저장되어 있어도 사용자는 B를 먼저 등록하고 A를 나중에 추가할 수 있습니다.
	 * 그래서 Repository에서 A→B와 B→A를 모두 equality 조건으로 조회합니다. 또한 HIRA CSV는 제품별
	 * raw 행이 여러 개일 수 있으므로 같은 정렬된 성분쌍은 하나의 evidence로 dedupe합니다.</p>
	 */
	public List<SafetyEvidence> checkCombination(DrugMaster drugA, DrugMaster drugB) {
		String ingredientA = normalizedIngredient(drugA);
		String ingredientB = normalizedIngredient(drugB);
		if (isBlank(ingredientA) || isBlank(ingredientB) || ingredientA.equals(ingredientB)) {
			return List.of();
		}

		List<DurComboContraindication> rows = comboRepository.findExactPair(ingredientA, ingredientB);
		Map<String, SafetyEvidence> deduped = new LinkedHashMap<>();
		for (DurComboContraindication row : rows) {
			SafetyEvidence evidence = new SafetyEvidence(
					"DUR",
					"병용금기",
					row.getIngredientNormA(),
					row.getIngredientNormB(),
					firstNotBlank(row.getDetail(), row.getNote(), "HIRA DUR 병용금기 성분쌍입니다."),
					row.getGazetteNo(),
					row.getGazetteDate()
			);
			deduped.putIfAbsent(evidence.dedupeKey(), evidence);
		}
		return new ArrayList<>(deduped.values());
	}

	/**
	 * 65세 이상 부모에게 새 약을 추가할 때 노인주의 여부를 검사합니다.
	 *
	 * <p>DUR 노인주의는 고령자에게만 의미가 있으므로 65세 미만이면 DB 조회 없이 빈 결과를 반환합니다.
	 * 일반 노인주의와 NSAID 노인주의 CSV가 분리되어 있어 두 테이블을 모두 정확 조회한 뒤 WARN 근거로 합칩니다.</p>
	 */
	public List<SafetyEvidence> checkElderly(DrugMaster drug, int age) {
		if (age < ELDERLY_AGE_THRESHOLD) {
			return List.of();
		}
		String ingredient = normalizedIngredient(drug);
		if (isBlank(ingredient)) {
			return List.of();
		}

		Map<String, SafetyEvidence> deduped = new LinkedHashMap<>();
		for (DurElderlyCaution row : elderlyCautionRepository.findByIngredientNorm(ingredient)) {
			SafetyEvidence evidence = new SafetyEvidence(
					"DUR",
					"노인주의",
					row.getIngredientNorm(),
					null,
					firstNotBlank(row.getDetail(), row.getNote(), "HIRA DUR 노인주의 성분입니다."),
					row.getGazetteNo(),
					row.getGazetteDate()
			);
			deduped.putIfAbsent(evidence.dedupeKey(), evidence);
		}
		for (DurElderlyNsaidCaution row : elderlyNsaidCautionRepository.findByIngredientNorm(ingredient)) {
			SafetyEvidence evidence = new SafetyEvidence(
					"DUR",
					"노인주의",
					row.getIngredientNorm(),
					null,
					firstNotBlank(row.getDetail(), "HIRA DUR NSAID 노인주의 성분입니다."),
					null,
					null
			);
			deduped.putIfAbsent(evidence.dedupeKey(), evidence);
		}
		return new ArrayList<>(deduped.values());
	}

	private static String normalizedIngredient(DrugMaster drug) {
		return drug == null ? null : drug.getMainIngrNorm();
	}

	private static String firstNotBlank(String... values) {
		for (String value : values) {
			if (!isBlank(value)) {
				return value;
			}
		}
		return null;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
