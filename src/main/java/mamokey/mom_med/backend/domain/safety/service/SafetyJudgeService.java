package mamokey.mom_med.backend.domain.safety.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.dur.service.DurRuleEngine;
import mamokey.mom_med.backend.domain.safety.model.SafetyDecision;
import mamokey.mom_med.backend.domain.safety.model.SafetyEvidence;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약 추가 시 전체 안전 판정을 조립하는 MVP 서비스입니다.
 *
 * <p>현재 Slice 02 범위에서는 DUR 1차 안전망만 사용합니다. 새 약과 기존 약을 하나씩 비교해
 * 병용금기 evidence를 모으고, 병용금기가 하나라도 있으면 즉시 BLOCK으로 판단합니다.
 * 병용금기가 없을 때만 65세 이상 노인주의를 검사해 WARN/ALLOW를 결정합니다.</p>
 */
@Service
@Transactional(readOnly = true)
public class SafetyJudgeService {

	private final DurRuleEngine durRuleEngine;

	public SafetyJudgeService(DurRuleEngine durRuleEngine) {
		this.durRuleEngine = durRuleEngine;
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

		List<SafetyEvidence> elderlyEvidences = durRuleEngine.checkElderly(newDrug, age);
		if (!elderlyEvidences.isEmpty()) {
			return new SafetyVerdict(SafetyDecision.WARN, elderlyEvidences);
		}

		return new SafetyVerdict(SafetyDecision.ALLOW, List.of());
	}
}
