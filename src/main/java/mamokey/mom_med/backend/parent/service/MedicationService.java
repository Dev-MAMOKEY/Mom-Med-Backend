package mamokey.mom_med.backend.parent.service;

import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.drug.repository.DrugMasterRepository;
import mamokey.mom_med.backend.domain.drug.service.DrugIdentifyService;
import mamokey.mom_med.backend.domain.safety.model.SafetyDecision;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import mamokey.mom_med.backend.domain.safety.service.SafetyJudgeService;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.global.exception.SafetyBlockException;
import mamokey.mom_med.backend.parent.domain.PatientMedication;
import mamokey.mom_med.backend.parent.domain.PatientProfile;
import mamokey.mom_med.backend.parent.dto.AddMedicationRequest;
import mamokey.mom_med.backend.parent.dto.AddMedicationResponse;
import mamokey.mom_med.backend.parent.dto.MedicationListResponse;
import mamokey.mom_med.backend.parent.dto.MedicationResponse;
import mamokey.mom_med.backend.parent.repository.PatientMedicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

/**
 * 부모 약장(복용 약) 관리 서비스 (Slice 04).
 *
 * 약 추가 흐름:
 * 1. 부모 존재 확인 + 나이 계산
 * 2. ref.drugs_master 조회 (미적재 시 MFDS API 자동 캐싱)
 * 3. DUR + NB 2겹 안전 검사
 * 4. logs.safety_check_log에 결과 기록 — REQUIRES_NEW로 BLOCK 롤백과 무관하게 보존
 * 5. BLOCK → 409, 중복 → 409, ALLOW/WARN → 201 + safety_check 포함
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MedicationService {

    private final PatientMedicationRepository medicationRepository;
    private final DrugMasterRepository drugMasterRepository;
    private final DrugIdentifyService drugIdentifyService;
    private final SafetyJudgeService safetyJudgeService;
    private final ParentService parentService;
    private final SafetyCheckLogWriter safetyCheckLogWriter;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    /**
     * 약장 목록 조회.
     * consent_data_share=false인 부모는 403 반환합니다.
     */
    public MedicationListResponse listMedications(UUID parentId) {
        parentService.findOrThrow(parentId);
        parentService.requireConsent(parentId);

        List<MedicationResponse> active = medicationRepository
                .findByParentIdAndDeletedAtIsNull(parentId)
                .stream()
                .map(MedicationResponse::from)
                .toList();

        List<MedicationResponse> history = medicationRepository
                .findByParentIdAndDeletedAtIsNotNull(parentId)
                .stream()
                .map(MedicationResponse::from)
                .toList();

        return new MedicationListResponse(active, history);
    }

    // ─── 생성 ─────────────────────────────────────────────────────────────

    /**
     * 약 추가 — DUR + NB 안전 검사 후 약장에 등록합니다.
     *
     * safety_check_log는 BLOCK 포함 모든 판정을 기록합니다.
     * SafetyCheckLogWriter의 REQUIRES_NEW 전파 덕분에 BLOCK으로 상위 트랜잭션이
     * 롤백되어도 로그는 커밋됩니다.
     */
    @Transactional
    public AddMedicationResponse addMedication(UUID parentId, AddMedicationRequest req) {
        PatientProfile profile = parentService.findOrThrow(parentId);
        int age = Period.between(profile.getBirthdate(), LocalDate.now()).getYears();

        DrugMaster newDrug = drugIdentifyService.fetchByItemSeq(req.itemSeq());

        List<String> activeItemSeqs = medicationRepository
                .findByParentIdAndDeletedAtIsNull(parentId)
                .stream()
                .map(PatientMedication::getItemSeq)
                .toList();
        List<DrugMaster> currentDrugs = drugMasterRepository.findAllById(activeItemSeqs);

        SafetyVerdict verdict = safetyJudgeService.judge(currentDrugs, newDrug, age, parentId, profile.isPregnant());

        // 로그 기록 — BLOCK도 포함, REQUIRES_NEW로 독립 커밋
        safetyCheckLogWriter.record(parentId, req.itemSeq(), verdict);

        if (verdict.decision() == SafetyDecision.BLOCK) {
            throw new SafetyBlockException(verdict);
        }

        if (medicationRepository.existsByParentIdAndItemSeqAndDeletedAtIsNull(parentId, req.itemSeq())) {
            throw new CustomException(ErrorCode.DUPLICATE_MEDICATION);
        }

        PatientMedication medication = PatientMedication.create(
                parentId,
                req.itemSeq(),
                newDrug.getItemName(),
                newDrug.getMainIngrNorm(),
                req.startedOn(),
                req.memo()
        );
        medicationRepository.save(medication);
        return AddMedicationResponse.from(medication, verdict);
    }

    // ─── 삭제 ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteMedication(UUID parentId, Long medicationId) {
        PatientMedication medication = medicationRepository
                .findByIdAndParentIdAndDeletedAtIsNull(medicationId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDICATION_NOT_FOUND));

        medication.softDelete();
    }
}
