package mamokey.mom_med.backend.parent.service;

import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.global.util.DrugNameNormalizer;
import mamokey.mom_med.backend.parent.domain.PatientAllergy;
import mamokey.mom_med.backend.parent.dto.AllergyListResponse;
import mamokey.mom_med.backend.parent.dto.AllergyResponse;
import mamokey.mom_med.backend.parent.dto.CreateAllergyRequest;
import mamokey.mom_med.backend.parent.event.ParentDataChangedEvent;
import mamokey.mom_med.backend.parent.repository.PatientAllergyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 알레르기 CRUD 서비스.
 *
 * <p>drug 타입 알레르기는 allergen_norm에 DrugNameNormalizer로 정규화된 약물명을 저장합니다.
 * SafetyJudge가 DUR 검사 시 이 값을 사용합니다.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AllergyService {

    private final PatientAllergyRepository allergyRepository;
    private final ParentService parentService;
    private final ApplicationEventPublisher eventPublisher;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    public AllergyListResponse listAllergies(UUID parentId) {
        // 부모 존재 확인
        parentService.findOrThrow(parentId);

        List<AllergyResponse> active = allergyRepository
                .findByParentIdAndDeletedAtIsNull(parentId)
                .stream()
                .map(AllergyResponse::from)
                .toList();

        List<AllergyResponse> history = allergyRepository
                .findByParentIdAndDeletedAtIsNotNull(parentId)
                .stream()
                .map(AllergyResponse::from)
                .toList();

        return new AllergyListResponse(active, history);
    }

    // ─── 생성 ─────────────────────────────────────────────────────────────

    @Transactional
    public AllergyResponse createAllergy(UUID parentId, CreateAllergyRequest req) {
        // 부모 존재 확인
        parentService.findOrThrow(parentId);

        // drug 타입인 경우 약물명 정규화
        String allergenNorm = null;
        if ("drug".equals(req.allergenType())) {
            allergenNorm = DrugNameNormalizer.normalize(req.allergenName());
        }

        PatientAllergy allergy = PatientAllergy.create(
                parentId,
                req.allergenType(),
                req.allergenName(),
                allergenNorm,
                req.severity(),
                req.notes(),
                req.confirmedAt()
        );

        allergyRepository.save(allergy);
        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));
        return AllergyResponse.from(allergy);
    }

    // ─── 삭제 ─────────────────────────────────────────────────────────────

    /**
     * 알레르기 soft delete.
     * deleted_at = NOW() 로 설정하며 이력은 보존됩니다.
     */
    @Transactional
    public void deleteAllergy(UUID parentId, Long allergyId) {
        PatientAllergy allergy = allergyRepository
                .findByIdAndParentId(allergyId, parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.ALLERGY_NOT_FOUND));

        if (allergy.isDeleted()) {
            throw new CustomException(ErrorCode.ALLERGY_NOT_FOUND);
        }

        allergy.softDelete();
        eventPublisher.publishEvent(new ParentDataChangedEvent(parentId));
    }
}
