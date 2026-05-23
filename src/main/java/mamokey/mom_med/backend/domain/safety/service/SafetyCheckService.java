package mamokey.mom_med.backend.domain.safety.service;

import java.util.List;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.drug.repository.DrugMasterRepository;
import mamokey.mom_med.backend.domain.safety.dto.SafetyCheckRequest;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * Safety API 요청을 도메인 판정 서비스에 연결하는 application service입니다.
 *
 * <p>Controller가 받은 ITEM_SEQ 목록을 약 마스터 Entity로 해석하는 책임을 가집니다.
 * ref.drugs_master에 없는 ITEM_SEQ는 안전판정 자체가 불가능하므로 404 오류로 반환합니다.</p>
 */
@Service
public class SafetyCheckService {

	private final DrugMasterRepository drugMasterRepository;
	private final SafetyJudgeService safetyJudgeService;

	public SafetyCheckService(
			DrugMasterRepository drugMasterRepository,
			SafetyJudgeService safetyJudgeService
	) {
		this.drugMasterRepository = drugMasterRepository;
		this.safetyJudgeService = safetyJudgeService;
	}

	public SafetyVerdict check(SafetyCheckRequest request) {
		DrugMaster newDrug = findDrugOrThrow(request.newDrug());
		List<DrugMaster> currentDrugs = request.currentDrugs().stream()
				.map(this::findDrugOrThrow)
				.toList();

		return safetyJudgeService.judge(currentDrugs, newDrug, request.age());
	}

	private DrugMaster findDrugOrThrow(String itemSeq) {
		return drugMasterRepository.findById(itemSeq)
				.orElseThrow(() -> new CustomException(
						ErrorCode.DRUG_NOT_FOUND,
						"약 마스터에서 ITEM_SEQ를 찾을 수 없습니다: " + itemSeq
				));
	}
}
