package mamokey.mom_med.backend.domain.safety.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.dur.service.DurRuleEngine;
import mamokey.mom_med.backend.domain.nb.entity.NbInteraction;
import mamokey.mom_med.backend.domain.nb.repository.NbInteractionRepository;
import mamokey.mom_med.backend.domain.nb.service.NbExtractionResult;
import mamokey.mom_med.backend.domain.nb.service.NbExtractionService;
import mamokey.mom_med.backend.domain.nb.util.DrugGroupDictionary;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.domain.safety.model.SafetyDecision;
import mamokey.mom_med.backend.domain.safety.model.SafetyEvidence;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import mamokey.mom_med.backend.parent.domain.PatientAllergy;
import mamokey.mom_med.backend.parent.domain.PatientCondition;
import mamokey.mom_med.backend.parent.repository.PatientAllergyRepository;
import mamokey.mom_med.backend.parent.repository.PatientConditionRepository;
import org.springframework.stereotype.Service;

/**
 * 약 추가 시 전체 안전 판정을 조립하는 서비스입니다.
 *
 * <p>3겹 안전망:</p>
 * <ol>
 *   <li>DUR 1차 (Slice 02): 병용금기·노인주의</li>
 *   <li>NB AI 2차 (Slice 03): drug_drug 상호작용</li>
 *   <li>Slice 05 확장: patient_class 환자분류 금기, DUR 연령금기, DUR 임부금기, 알레르기</li>
 * </ol>
 *
 * <p>병용금기가 하나라도 있으면 즉시 BLOCK을 반환합니다.
 * 그 외 evidence는 모아서 BLOCK &gt; WARN &gt; INFO &gt; ALLOW 순으로 통합 판정합니다.</p>
 */
@Service
public class SafetyJudgeService {

	/** KCD 범위 파싱 패턴: "K70-K77", "I50", "D60-D64,D70-D77" 형태 */
	private static final Pattern KCD_RANGE_PATTERN = Pattern.compile("([A-Z]\\d+)(?:-([A-Z]?\\d+))?");

	private final DurRuleEngine durRuleEngine;
	private final NbExtractionService nbExtractionService;
	private final NbInteractionRepository nbInteractionRepository;
	private final PatientConditionRepository patientConditionRepository;
	private final PatientAllergyRepository patientAllergyRepository;

	public SafetyJudgeService(
			DurRuleEngine durRuleEngine,
			NbExtractionService nbExtractionService,
			NbInteractionRepository nbInteractionRepository,
			PatientConditionRepository patientConditionRepository,
			PatientAllergyRepository patientAllergyRepository
	) {
		this.durRuleEngine = durRuleEngine;
		this.nbExtractionService = nbExtractionService;
		this.nbInteractionRepository = nbInteractionRepository;
		this.patientConditionRepository = patientConditionRepository;
		this.patientAllergyRepository = patientAllergyRepository;
	}

	/**
	 * 안전 판정 메인 메서드 (Slice 02~05 통합).
	 *
	 * @param currentDrugs 현재 복용 중인 약 목록
	 * @param newDrug      추가하려는 신규 약
	 * @param age          부모 나이
	 * @param parentId     부모 UUID (기저질환·알레르기 조회용). null이면 Slice 05 추가 검사 생략.
	 * @param isPregnant   임신 여부 (true면 임부금기 검사 수행)
	 */
	public SafetyVerdict judge(
			List<DrugMaster> currentDrugs,
			DrugMaster newDrug,
			int age,
			UUID parentId,
			boolean isPregnant
	) {
		Map<String, SafetyEvidence> combinationEvidences = new LinkedHashMap<>();

		// ── 1차: DUR 병용금기 ────────────────────────────────────────────────
		for (DrugMaster currentDrug : currentDrugs) {
			for (SafetyEvidence evidence : durRuleEngine.checkCombination(currentDrug, newDrug)) {
				combinationEvidences.putIfAbsent(evidence.dedupeKey(), evidence);
			}
		}

		if (!combinationEvidences.isEmpty()) {
			return new SafetyVerdict(SafetyDecision.BLOCK, new ArrayList<>(combinationEvidences.values()));
		}

		// ── 2차 이후: 노인주의 + NB drug_drug + Slice 05 추가 검사 ─────────────
		Map<String, SafetyEvidence> evidences = new LinkedHashMap<>();

		for (SafetyEvidence evidence : durRuleEngine.checkElderly(newDrug, age)) {
			evidences.putIfAbsent(evidence.dedupeKey(), evidence);
		}
		for (SafetyEvidence evidence : checkNbBidirectionally(currentDrugs, newDrug)) {
			evidences.putIfAbsent(evidence.dedupeKey(), evidence);
		}

		// ── Slice 05: DUR 연령금기 ───────────────────────────────────────────
		for (SafetyEvidence evidence : durRuleEngine.checkAgeContraindication(newDrug, age)) {
			evidences.putIfAbsent(evidence.dedupeKey(), evidence);
		}

		// ── Slice 05: DUR 임부금기 ───────────────────────────────────────────
		if (isPregnant) {
			for (SafetyEvidence evidence : durRuleEngine.checkPregnancyContraindication(newDrug)) {
				evidences.putIfAbsent(evidence.dedupeKey(), evidence);
			}
		}

		// ── Slice 05: 부모 기저질환 × NB patient_class ──────────────────────
		if (parentId != null) {
			for (SafetyEvidence evidence : checkPatientClassContraindication(parentId, newDrug)) {
				evidences.putIfAbsent(evidence.dedupeKey(), evidence);
			}
			// ── Slice 05: 알레르기 ───────────────────────────────────────────
			for (SafetyEvidence evidence : checkAllergy(parentId, newDrug)) {
				evidences.putIfAbsent(evidence.dedupeKey(), evidence);
			}
		}

		return new SafetyVerdict(decide(evidences.values().stream().toList()), new ArrayList<>(evidences.values()));
	}

	/**
	 * 기존 호환성 오버로드 (Slice 02·03용). parentId=null, isPregnant=false로 위임합니다.
	 *
	 * @deprecated Slice 05 이후에는 {@link #judge(List, DrugMaster, int, UUID, boolean)} 사용
	 */
	@Deprecated
	public SafetyVerdict judge(List<DrugMaster> currentDrugs, DrugMaster newDrug, int age) {
		return judge(currentDrugs, newDrug, age, null, false);
	}

	// ── Slice 05: 환자분류 금기 ──────────────────────────────────────────────

	/**
	 * 부모 기저질환(KCD 코드)과 새 약의 NB patient_class entry를 교차 매칭합니다.
	 *
	 * <p>예) 부모 KCD = {K25}, 약 NB patient_class_kcd = "K25-K27" → 매칭 → WARN evidence 생성.</p>
	 */
	private List<SafetyEvidence> checkPatientClassContraindication(UUID parentId, DrugMaster newDrug) {
		List<PatientCondition> conditions = patientConditionRepository.findByParentIdAndDeletedAtIsNull(parentId);
		if (conditions.isEmpty()) {
			return List.of();
		}

		Set<String> parentKcdSet = conditions.stream()
				.map(PatientCondition::getKcdCode)
				.filter(kcd -> kcd != null && !kcd.isBlank())
				.collect(Collectors.toSet());
		if (parentKcdSet.isEmpty()) {
			return List.of();
		}

		List<NbInteraction> patientClassRows = nbInteractionRepository
				.findByItemSeqAndEntryTypeOrderByIdAsc(newDrug.getItemSeq(), NbInteraction.ENTRY_TYPE_PATIENT_CLASS);

		List<SafetyEvidence> evidences = new ArrayList<>();
		for (NbInteraction row : patientClassRows) {
			if (row.getPatientClassKcd() == null || row.getPatientClassKcd().isBlank()) {
				continue;
			}
			Set<String> matched = matchedKcdCodes(row.getPatientClassKcd(), parentKcdSet);
			if (!matched.isEmpty()) {
				SafetyDecision decision = decisionFromNbRisk(row.getRiskLevel());
				if (decision == SafetyDecision.ALLOW) {
					continue;
				}
				evidences.add(new SafetyEvidence(
						"NB_patient_class",
						"환자분류금기",
						newDrug.getMainIngrNorm(),
						null,
						row.getReasonSummary(),
						null,
						null,
						row.getRiskLevel(),
						null,
						row.getSourceQuote(),
						newDrug.getItemSeq(),
						row.getPatientClassText(),
						new ArrayList<>(matched),
						null,
						null
				));
			}
		}
		return evidences;
	}

	// ── Slice 05: 알레르기 ───────────────────────────────────────────────────

	/**
	 * 부모의 약물 알레르기와 새 약의 주성분을 교차 매칭합니다.
	 *
	 * <p>allergen_type='drug'이고 allergen_norm이 새 약 main_ingr_norm과 일치하면 BLOCK evidence를 생성합니다.</p>
	 */
	private List<SafetyEvidence> checkAllergy(UUID parentId, DrugMaster newDrug) {
		if (newDrug.getMainIngrNorm() == null || newDrug.getMainIngrNorm().isBlank()) {
			return List.of();
		}

		List<PatientAllergy> allergies = patientAllergyRepository.findByParentIdAndDeletedAtIsNull(parentId);
		List<SafetyEvidence> evidences = new ArrayList<>();
		for (PatientAllergy allergy : allergies) {
			if (!"drug".equals(allergy.getAllergenType())) {
				continue;
			}
			if (allergy.getAllergenNorm() == null || allergy.getAllergenNorm().isBlank()) {
				continue;
			}
			if (allergy.getAllergenNorm().equals(newDrug.getMainIngrNorm())) {
				evidences.add(new SafetyEvidence(
						"ALLERGY",
						"알레르기",
						newDrug.getMainIngrNorm(),
						null,
						"등록된 약물 알레르기 성분과 일치합니다.",
						null,
						null,
						"동시투여피해야함",
						null,
						null,
						newDrug.getItemSeq(),
						null,
						null,
						allergy.getAllergenName(),
						allergy.getSeverity()
				));
			}
		}
		return evidences;
	}

	// ── KCD 매핑 유틸 ────────────────────────────────────────────────────────

	/**
	 * 부모 KCD 집합과 rule_kcd 범위가 교차하는 부모 코드들을 반환합니다.
	 *
	 * <p>rule_kcd 예: "K70-K77", "I50,I20-I25", "D60-D64,D70-D77"</p>
	 */
	static Set<String> matchedKcdCodes(String ruleKcd, Set<String> parentKcdSet) {
		if (ruleKcd == null || ruleKcd.isBlank() || parentKcdSet.isEmpty()) {
			return Set.of();
		}
		Set<String> expanded = expandKcdRange(ruleKcd);
		return parentKcdSet.stream()
				.filter(parentCode -> expanded.stream()
						.anyMatch(ekcd -> parentCode.equals(ekcd)
								|| parentCode.startsWith(ekcd)
								|| ekcd.startsWith(parentCode)))
				.collect(Collectors.toSet());
	}

	/**
	 * KCD 범위 문자열을 개별 코드 Set으로 확장합니다.
	 *
	 * <p>"K70-K77" → {K70, K71, K72, K73, K74, K75, K76, K77}
	 * "I50,I20-I25" → {I50, I20, I21, I22, I23, I24, I25}</p>
	 */
	static Set<String> expandKcdRange(String ruleKcd) {
		Set<String> result = new java.util.LinkedHashSet<>();
		// 쉼표 분리 후 각 토큰 처리
		for (String token : ruleKcd.split(",")) {
			token = token.strip();
			Matcher m = KCD_RANGE_PATTERN.matcher(token);
			if (!m.matches()) {
				continue;
			}
			String startCode = m.group(1); // 예: "K70"
			String endSuffix = m.group(2); // 예: "77" 또는 null

			if (endSuffix == null) {
				result.add(startCode);
				continue;
			}

			// 시작 코드에서 알파벳 prefix + 숫자 추출
			String prefix = startCode.replaceAll("\\d.*", ""); // "K"
			String startNumStr = startCode.substring(prefix.length()); // "70"

			// endSuffix는 숫자만이거나 전체 코드일 수 있음 (예: "K77" 또는 "77")
			String endNumStr = endSuffix.replaceAll("[A-Z]", ""); // 알파벳 제거 → "77"

			try {
				int startNum = Integer.parseInt(startNumStr);
				int endNum = Integer.parseInt(endNumStr);
				for (int i = startNum; i <= endNum; i++) {
					result.add(prefix + i);
				}
			}
			catch (NumberFormatException e) {
				result.add(startCode); // 파싱 실패 시 시작 코드만 추가
			}
		}
		return result;
	}

	// ── NB drug_drug 양방향 검사 ────────────────────────────────────────────

	/**
	 * 새 약의 NB와 기존 약들의 NB를 모두 확인합니다.
	 *
	 * <p>NB 문서는 "이 약이 상대 약을 조심하라"고 쓰일 수도 있고, 기존 약 문서가 새 약을 조심하라고
	 * 쓸 수도 있습니다. 따라서 새 약 → 기존 약, 기존 약 → 새 약 양방향을 모두 확인해야 DUR 누락분을 회수할 수 있습니다.</p>
	 */
	private List<SafetyEvidence> checkNbBidirectionally(List<DrugMaster> currentDrugs, DrugMaster newDrug) {
		List<SafetyEvidence> evidences = new ArrayList<>();

		NbExtractionResult newDrugExtraction = extractNbSafely(newDrug);
		for (NbInteraction interaction : newDrugExtraction.interactions()) {
			if (!NbInteraction.ENTRY_TYPE_DRUG_DRUG.equals(interaction.getEntryType())) {
				continue;
			}
			for (DrugMaster currentDrug : currentDrugs) {
				if (matchesNbPartner(interaction, currentDrug)) {
					toEvidence(interaction, newDrug, currentDrug).ifPresent(evidences::add);
				}
			}
		}

		for (DrugMaster currentDrug : currentDrugs) {
			NbExtractionResult currentDrugExtraction = extractNbSafely(currentDrug);
			for (NbInteraction interaction : currentDrugExtraction.interactions()) {
				if (!NbInteraction.ENTRY_TYPE_DRUG_DRUG.equals(interaction.getEntryType())) {
					continue;
				}
				if (matchesNbPartner(interaction, newDrug)) {
					toEvidence(interaction, currentDrug, newDrug).ifPresent(evidences::add);
				}
			}
		}

		return evidences;
	}

	private NbExtractionResult extractNbSafely(DrugMaster drug) {
		if (drug.getNbDocData() == null || drug.getNbDocData().isBlank()) {
			return nbExtractionService.findVerifiedOptional(drug.getItemSeq())
					.orElseGet(() -> new NbExtractionResult(null, List.of(), true));
		}

		try {
			return nbExtractionService.extract(drug.getItemSeq());
		}
		catch (CustomException exception) {
			if (exception.getErrorCode() == ErrorCode.INVALID_INPUT || exception.getErrorCode() == ErrorCode.NOT_FOUND) {
				return new NbExtractionResult(null, List.of(), true);
			}
			throw exception;
		}
		catch (Exception exception) {
			// Gemini API 키 오류·네트워크 장애 등 외부 장애 시 NB 근거 없음으로 graceful degradation.
			return new NbExtractionResult(null, List.of(), true);
		}
	}

	private boolean matchesNbPartner(NbInteraction interaction, DrugMaster drug) {
		if (interaction.isDrugGroup()) {
			String atcCode = drug.getAtcCode();
			if (atcCode == null || atcCode.isBlank()) {
				return false;
			}
			return DrugGroupDictionary.prefixesFor(interaction.getPartnerDrugKo()).stream()
					.anyMatch(atcCode::startsWith);
		}
		return interaction.getPartnerDrugNorm() != null
				&& interaction.getPartnerDrugNorm().equals(drug.getMainIngrNorm());
	}

	private java.util.Optional<SafetyEvidence> toEvidence(
			NbInteraction interaction,
			DrugMaster sourceDrug,
			DrugMaster matchedDrug
	) {
		SafetyDecision decision = decisionFromNbRisk(interaction.getRiskLevel());
		if (decision == SafetyDecision.ALLOW) {
			return java.util.Optional.empty();
		}

		return java.util.Optional.of(new SafetyEvidence(
				"NB",
				"NB",
				sourceDrug.getMainIngrNorm(),
				matchedDrug.getMainIngrNorm(),
				interaction.getReasonSummary(),
				null,
				null,
				interaction.getRiskLevel(),
				interaction.getPartnerDrugKo(),
				interaction.getSourceQuote(),
				sourceDrug.getItemSeq(),
				null,
				null,
				null,
				null
		));
	}

	// ── 판정 통합 ────────────────────────────────────────────────────────────

	private SafetyDecision decide(List<SafetyEvidence> evidences) {
		// 알레르기 또는 임부금기는 BLOCK
		if (evidences.stream().anyMatch(e -> "알레르기".equals(e.type()) || "임부금기".equals(e.type()))) {
			return SafetyDecision.BLOCK;
		}
		if (evidences.stream().anyMatch(evidence -> evidence.riskLevel() != null
				&& decisionFromNbRisk(evidence.riskLevel()) == SafetyDecision.BLOCK)) {
			return SafetyDecision.BLOCK;
		}
		if (evidences.stream().anyMatch(evidence -> "노인주의".equals(evidence.type())
				|| "연령금기".equals(evidence.type())
				|| "환자분류금기".equals(evidence.type())
				|| (evidence.riskLevel() != null && decisionFromNbRisk(evidence.riskLevel()) == SafetyDecision.WARN))) {
			return SafetyDecision.WARN;
		}
		if (evidences.stream().anyMatch(evidence -> evidence.riskLevel() != null
				&& decisionFromNbRisk(evidence.riskLevel()) == SafetyDecision.INFO)) {
			return SafetyDecision.INFO;
		}
		return SafetyDecision.ALLOW;
	}

	private SafetyDecision decisionFromNbRisk(String riskLevel) {
		return switch (riskLevel == null ? "" : riskLevel) {
			case "동시투여피해야함" -> SafetyDecision.BLOCK;
			case "권장하지않음", "주의" -> SafetyDecision.WARN;
			default -> SafetyDecision.ALLOW;
		};
	}
}
