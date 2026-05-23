package mamokey.mom_med.backend.parent.service;

import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.domain.drug.entity.DrugMaster;
import mamokey.mom_med.backend.domain.drug.repository.DrugMasterRepository;
import mamokey.mom_med.backend.domain.drug.service.DrugIdentifyService;
import mamokey.mom_med.backend.domain.safety.model.SafetyDecision;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import mamokey.mom_med.backend.domain.safety.service.SafetyJudgeService;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;  // DUPLICATE_MEDICATION, MEDICATION_NOT_FOUND
import mamokey.mom_med.backend.global.exception.SafetyBlockException;
import mamokey.mom_med.backend.parent.domain.PatientMedication;
import mamokey.mom_med.backend.parent.domain.PatientProfile;
import mamokey.mom_med.backend.parent.dto.AddMedicationRequest;
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
 * <p>약 추가 시 Track A의 DUR 병용금기 엔진({@link SafetyJudgeService})을 호출합니다.
 * BLOCK 판정이면 {@link SafetyBlockException}(HTTP 409)을 던져 프론트가 처리합니다.
 * ref.drugs_master가 아직 적재되지 않은 경우 {@link ErrorCode#DRUG_NOT_FOUND}가 반환됩니다.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MedicationService {

    private final PatientMedicationRepository medicationRepository;
    private final DrugMasterRepository drugMasterRepository;
    private final DrugIdentifyService drugIdentifyService;  // itemSeq → MFDS 자동 캐싱
    private final SafetyJudgeService safetyJudgeService;   // Track A의 DUR 엔진
    private final ParentService parentService;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    public MedicationListResponse listMedications(UUID parentId) {
        parentService.findOrThrow(parentId);

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
     * 약 추가 — DUR 안전 검사 후 약장에 등록합니다.
     *
     * <ol>
     *   <li>DrugMaster에서 새 약 조회 (미적재 시 404)</li>
     *   <li>현재 활성 약장 목록으로 DUR 병용금기·노인주의 검사</li>
     *   <li>BLOCK이면 HTTP 409 + verdict 반환</li>
     *   <li>중복 확인 후 저장</li>
     * </ol>
     */
    @Transactional
    public MedicationResponse addMedication(UUID parentId, AddMedicationRequest req) {
        // 1. 부모 존재 확인 및 나이 계산
        PatientProfile profile = parentService.findOrThrow(parentId);
        int age = Period.between(profile.getBirthdate(), LocalDate.now()).getYears();

        // 2. 새 약을 DrugMaster에서 조회 — 캐시 없으면 MFDS API에서 자동 가져와 저장
        //    (identify 후보 목록에서 선택한 경우 캐시가 없을 수 있음)
        DrugMaster newDrug = drugIdentifyService.fetchByItemSeq(req.itemSeq());

        // 3. 현재 활성 약장의 DrugMaster 목록 (DUR 병용 비교 대상)
        List<String> activeItemSeqs = medicationRepository
                .findByParentIdAndDeletedAtIsNull(parentId)
                .stream()
                .map(PatientMedication::getItemSeq)
                .toList();
        List<DrugMaster> currentDrugs = drugMasterRepository.findAllById(activeItemSeqs);

        // 4. DUR 안전 검사 (BLOCK → 409, WARN/ALLOW → 통과)
        SafetyVerdict verdict = safetyJudgeService.judge(currentDrugs, newDrug, age);
        if (verdict.decision() == SafetyDecision.BLOCK) {
            throw new SafetyBlockException(verdict);
        }

        // 5. 중복 약 등록 방지 (partial unique index가 DB에서도 보장하지만 명확한 에러 메시지용)
        if (medicationRepository.existsByParentIdAndItemSeqAndDeletedAtIsNull(parentId, req.itemSeq())) {
            throw new CustomException(ErrorCode.DUPLICATE_MEDICATION);
        }

        // 6. 저장
        PatientMedication medication = PatientMedication.create(
                parentId,
                req.itemSeq(),
                newDrug.getItemName(),           // DrugMaster에서 공식 약품명 사용
                newDrug.getMainIngrNorm(),        // 정규화 성분명 캐싱
                req.startedOn(),
                req.memo()
        );
        medicationRepository.save(medication);
        return MedicationResponse.from(medication);
    }

    // ─── 삭제 ─────────────────────────────────────────────────────────────

    /**
     * 약 soft delete — 이력은 보존됩니다.
     */
    @Transactional
    public void deleteMedication(UUID parentId, Long medicationId) {
        PatientMedication medication = medicationRepository
                .findByIdAndParentIdAndDeletedAtIsNull(medicationId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDICATION_NOT_FOUND));

        medication.softDelete();
    }

}

