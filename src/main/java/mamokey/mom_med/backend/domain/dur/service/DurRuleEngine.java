package mamokey.mom_med.backend.domain.dur.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.dur.entity.DurAgeContraindication;
import mamokey.mom_med.backend.domain.dur.entity.DurComboContraindication;
import mamokey.mom_med.backend.domain.dur.entity.DurElderlyCaution;
import mamokey.mom_med.backend.domain.dur.entity.DurElderlyNsaidCaution;
import mamokey.mom_med.backend.domain.dur.entity.DurPregnancyContraindication;
import mamokey.mom_med.backend.domain.dur.repository.DurAgeContraindicationRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurComboContraindicationRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurElderlyCautionRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurElderlyNsaidCautionRepository;
import mamokey.mom_med.backend.domain.dur.repository.DurPregnancyContraindicationRepository;
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
	/** age_limit 파싱 패턴: "12세미만", "65세이상", "3개월미만" 형태를 인식합니다. */
	private static final Pattern AGE_LIMIT_PATTERN = Pattern.compile("(\\d+)(세|개월)(미만|이하|이상|초과)");

	private final DurComboContraindicationRepository comboRepository;
	private final DurElderlyCautionRepository elderlyCautionRepository;
	private final DurElderlyNsaidCautionRepository elderlyNsaidCautionRepository;
	private final DurAgeContraindicationRepository ageContraindicationRepository;
	private final DurPregnancyContraindicationRepository pregnancyContraindicationRepository;

	public DurRuleEngine(
			DurComboContraindicationRepository comboRepository,
			DurElderlyCautionRepository elderlyCautionRepository,
			DurElderlyNsaidCautionRepository elderlyNsaidCautionRepository,
			DurAgeContraindicationRepository ageContraindicationRepository,
			DurPregnancyContraindicationRepository pregnancyContraindicationRepository
	) {
		this.comboRepository = comboRepository;
		this.elderlyCautionRepository = elderlyCautionRepository;
		this.elderlyNsaidCautionRepository = elderlyNsaidCautionRepository;
		this.ageContraindicationRepository = ageContraindicationRepository;
		this.pregnancyContraindicationRepository = pregnancyContraindicationRepository;
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

	/**
	 * DUR 연령금기 CSV를 검사합니다 (Slice 05).
	 *
	 * <p>새 약 성분의 연령금기 행을 조회하고, age_limit 문자열을 파싱해 부모 나이가 해당 조건에
	 * 맞으면 BLOCK evidence를 생성합니다. 예) "12세미만" → 부모 나이 &lt; 12이면 BLOCK.</p>
	 */
	public List<SafetyEvidence> checkAgeContraindication(DrugMaster drug, int age) {
		String ingredient = normalizedIngredient(drug);
		if (isBlank(ingredient)) {
			return List.of();
		}

		Map<String, SafetyEvidence> deduped = new LinkedHashMap<>();
		for (DurAgeContraindication row : ageContraindicationRepository.findByIngredientNorm(ingredient)) {
			if (!appliesToAge(row.getAgeLimit(), age)) {
				continue;
			}
			SafetyEvidence evidence = new SafetyEvidence(
					"DUR",
					"연령금기",
					row.getIngredientNorm(),
					null,
					firstNotBlank(row.getAgeLimit(), row.getDetail(), "HIRA DUR 연령금기 성분입니다."),
					row.getGazetteNo(),
					row.getGazetteDate()
			);
			deduped.putIfAbsent(evidence.dedupeKey(), evidence);
		}
		return new ArrayList<>(deduped.values());
	}

	/**
	 * DUR 임부금기 CSV를 검사합니다 (Slice 05).
	 *
	 * <p>is_pregnant=true인 부모에게만 호출합니다. 임부금기 행이 있으면 BLOCK evidence를 생성합니다.</p>
	 */
	public List<SafetyEvidence> checkPregnancyContraindication(DrugMaster drug) {
		String ingredient = normalizedIngredient(drug);
		if (isBlank(ingredient)) {
			return List.of();
		}

		Map<String, SafetyEvidence> deduped = new LinkedHashMap<>();
		for (DurPregnancyContraindication row : pregnancyContraindicationRepository.findByIngredientNorm(ingredient)) {
			SafetyEvidence evidence = new SafetyEvidence(
					"DUR",
					"임부금기",
					row.getIngredientNorm(),
					null,
					firstNotBlank(row.getDetail(), "HIRA DUR 임부금기 성분입니다."),
					row.getGazetteNo(),
					row.getGazetteDate()
			);
			deduped.putIfAbsent(evidence.dedupeKey(), evidence);
		}
		return new ArrayList<>(deduped.values());
	}

	/**
	 * age_limit 문자열이 부모 나이에 해당하는지 판단합니다.
	 *
	 * <p>패턴 인식 가능: "12세미만", "18세이하", "65세이상", "3개월미만"
	 * 파싱 실패 시 안전을 위해 false(미적용)로 처리합니다.</p>
	 */
	static boolean appliesToAge(String ageLimit, int parentAge) {
		if (isBlank(ageLimit)) {
			return false;
		}
		Matcher matcher = AGE_LIMIT_PATTERN.matcher(ageLimit.replaceAll("\\s+", ""));
		if (!matcher.find()) {
			return false; // 파싱 불가 → 적용하지 않음
		}
		int threshold = Integer.parseInt(matcher.group(1));
		String unit = matcher.group(2);
		String condition = matcher.group(3);
		// 개월 단위를 연 단위로 환산 (소수점 내림)
		int thresholdYears = "개월".equals(unit) ? threshold / 12 : threshold;
		return switch (condition) {
			case "미만" -> parentAge < thresholdYears;
			case "이하" -> parentAge <= thresholdYears;
			case "이상" -> parentAge >= thresholdYears;
			case "초과" -> parentAge > thresholdYears;
			default -> false;
		};
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
