package mamokey.mom_med.backend.domain.safety.service;

import java.util.List;
import java.util.UUID;

import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.drug.repository.DrugMasterRepository;
import mamokey.mom_med.backend.domain.safety.dto.SafetyCheckRequest;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.parent.domain.PatientProfile;
import mamokey.mom_med.backend.parent.repository.PatientProfileRepository;
import org.springframework.stereotype.Service;

/**
 * Safety API 요청을 도메인 판정 서비스에 연결하는 application service입니다.
 *
 * <p>Controller가 받은 ITEM_SEQ 목록을 약 마스터 Entity로 해석하는 책임을 가집니다.
 * Slice 05에서 parentId로 부모 프로필을 조회해 임신 여부를 전달합니다.
 * ref.drugs_master에 없는 ITEM_SEQ는 안전판정 자체가 불가능하므로 404 오류로 반환합니다.</p>
 */
@Service
public class SafetyCheckService {

	private final DrugMasterRepository drugMasterRepository;
	private final PatientProfileRepository patientProfileRepository;
	private final SafetyJudgeService safetyJudgeService;

	public SafetyCheckService(
			DrugMasterRepository drugMasterRepository,
			PatientProfileRepository patientProfileRepository,
			SafetyJudgeService safetyJudgeService
	) {
		this.drugMasterRepository = drugMasterRepository;
		this.patientProfileRepository = patientProfileRepository;
		this.safetyJudgeService = safetyJudgeService;
	}

	public SafetyVerdict check(SafetyCheckRequest request) {
		DrugMaster newDrug = findDrugOrThrow(request.newDrug());
		List<DrugMaster> currentDrugs = request.currentDrugs().stream()
				.map(this::findDrugOrThrow)
				.toList();

		// parentId로 부모 프로필 조회 — 없으면 null로 graceful degradation (Slice 05 검사 생략)
		UUID parentId = null;
		boolean isPregnant = false;
		if (request.parentId() != null && !request.parentId().isBlank()) {
			try {
				UUID pid = UUID.fromString(request.parentId());
				PatientProfile profile = patientProfileRepository.findById(pid).orElse(null);
				if (profile != null) {
					parentId = pid;
					isPregnant = profile.isPregnant();
				}
			}
			catch (IllegalArgumentException ignored) {
				// 잘못된 UUID 형식 → parentId=null로 처리
			}
		}

		return safetyJudgeService.judge(currentDrugs, newDrug, request.age(), parentId, isPregnant);
	}

	private DrugMaster findDrugOrThrow(String itemSeq) {
		return drugMasterRepository.findById(itemSeq)
				.orElseThrow(() -> new CustomException(
						ErrorCode.DRUG_NOT_FOUND,
						"약 마스터에서 ITEM_SEQ를 찾을 수 없습니다: " + itemSeq
				));
	}
}
