package mamokey.mom_med.backend.domain.safety.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.dur.service.DurRuleEngine;
import mamokey.mom_med.backend.domain.nb.entity.NbInteraction;
import mamokey.mom_med.backend.domain.nb.service.NbExtractionResult;
import mamokey.mom_med.backend.domain.nb.service.NbExtractionService;
import mamokey.mom_med.backend.domain.nb.util.DrugGroupDictionary;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.domain.safety.model.SafetyDecision;
import mamokey.mom_med.backend.domain.safety.model.SafetyEvidence;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import org.springframework.stereotype.Service;

/**
 * 약 추가 시 전체 안전 판정을 조립하는 MVP 서비스입니다.
 *
 * <p>현재 Slice 02 범위에서는 DUR 1차 안전망만 사용합니다. 새 약과 기존 약을 하나씩 비교해
 * 병용금기 evidence를 모으고, 병용금기가 하나라도 있으면 즉시 BLOCK으로 판단합니다.
 * 병용금기가 없을 때만 65세 이상 노인주의를 검사해 WARN/ALLOW를 결정합니다.</p>
 */
@Service
public class SafetyJudgeService {

	private final DurRuleEngine durRuleEngine;
	private final NbExtractionService nbExtractionService;

	public SafetyJudgeService(DurRuleEngine durRuleEngine, NbExtractionService nbExtractionService) {
		this.durRuleEngine = durRuleEngine;
		this.nbExtractionService = nbExtractionService;
	}

	public SafetyVerdict judge(List<DrugMaster> currentDrugs, DrugMaster newDrug, int age) {
		Map<String, SafetyEvidence> combinationEvidences = new LinkedHashMap<>();

		// 새 약은 부모가 이미 복용 중인 모든 약과 비교해야 합니다.
		// 같은 성분쌍이 여러 기존 약 또는 여러 raw DUR 행에서 반복될 수 있어 evidence key로 dedupe합니다.
		for (DrugMaster currentDrug : currentDrugs) {
			for (SafetyEvidence evidence : durRuleEngine.checkCombination(currentDrug, newDrug)) {
				combinationEvidences.putIfAbsent(evidence.dedupeKey(), evidence);
			}
		}

		if (!combinationEvidences.isEmpty()) {
			return new SafetyVerdict(SafetyDecision.BLOCK, new ArrayList<>(combinationEvidences.values()));
		}

		Map<String, SafetyEvidence> evidences = new LinkedHashMap<>();
		for (SafetyEvidence evidence : durRuleEngine.checkElderly(newDrug, age)) {
			evidences.putIfAbsent(evidence.dedupeKey(), evidence);
		}
		for (SafetyEvidence evidence : checkNbBidirectionally(currentDrugs, newDrug)) {
			evidences.putIfAbsent(evidence.dedupeKey(), evidence);
		}

		return new SafetyVerdict(decide(evidences.values().stream().toList()), new ArrayList<>(evidences.values()));
	}

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
			for (DrugMaster currentDrug : currentDrugs) {
				if (matchesNbPartner(interaction, currentDrug)) {
					toEvidence(interaction, newDrug, currentDrug).ifPresent(evidences::add);
				}
			}
		}

		for (DrugMaster currentDrug : currentDrugs) {
			NbExtractionResult currentDrugExtraction = extractNbSafely(currentDrug);
			for (NbInteraction interaction : currentDrugExtraction.interactions()) {
				if (matchesNbPartner(interaction, newDrug)) {
					toEvidence(interaction, currentDrug, newDrug).ifPresent(evidences::add);
				}
			}
		}

		return evidences;
	}

	private NbExtractionResult extractNbSafely(DrugMaster drug) {
		// 약 마스터에 NB_DOC_DATA가 없으면 LLM 추출 자체를 시도할 수 없습니다.
		// 그래도 이미 검증된 캐시가 있으면 재사용하고, 캐시도 없을 때만 "NB 근거 없음"으로 처리합니다.
		// 예외를 던져 정상 분기를 표현하면 상위 safety/check 트랜잭션이 rollback-only가 되어 500으로 변할 수 있습니다.
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
			// DUR 판정은 정상 진행됩니다.
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
				sourceDrug.getItemSeq()
		));
	}

	private SafetyDecision decide(List<SafetyEvidence> evidences) {
		if (evidences.stream().anyMatch(evidence -> evidence.riskLevel() != null
				&& decisionFromNbRisk(evidence.riskLevel()) == SafetyDecision.BLOCK)) {
			return SafetyDecision.BLOCK;
		}
		if (evidences.stream().anyMatch(evidence -> "노인주의".equals(evidence.type())
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
