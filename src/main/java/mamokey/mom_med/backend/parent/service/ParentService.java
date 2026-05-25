package mamokey.mom_med.backend.parent.service;

import lombok.RequiredArgsConstructor;
import mamokey.mom_med.backend.domain.weather.service.ParentGridService;
import mamokey.mom_med.backend.global.exception.CustomException;
import mamokey.mom_med.backend.global.exception.ErrorCode;
import mamokey.mom_med.backend.parent.domain.PatientProfile;
import mamokey.mom_med.backend.parent.dto.CreateParentRequest;
import mamokey.mom_med.backend.parent.dto.ParentProfileResponse;
import mamokey.mom_med.backend.parent.dto.UpdateParentRequest;
import mamokey.mom_med.backend.parent.repository.PatientAllergyRepository;
import mamokey.mom_med.backend.parent.repository.PatientMedicationRepository;
import mamokey.mom_med.backend.parent.repository.PatientProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 부모 프로파일 CRUD 서비스.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ParentService {

    private final PatientProfileRepository profileRepository;
    private final PatientMedicationRepository medicationRepository;
    private final PatientAllergyRepository allergyRepository;
    private final ParentGridService parentGridService;

    // ─── 조회 ─────────────────────────────────────────────────────────────

    public ParentProfileResponse getProfile(UUID parentId) {
        PatientProfile profile = findOrThrow(parentId);
        int medicationCount = (int) medicationRepository.countByParentIdAndDeletedAtIsNull(parentId);
        int allergyCount = (int) allergyRepository.countByParentIdAndDeletedAtIsNull(parentId);
        return ParentProfileResponse.from(profile, medicationCount, allergyCount);
    }

    // ─── 생성 ─────────────────────────────────────────────────────────────

    @Transactional
    public ParentProfileResponse createProfile(CreateParentRequest req) {
        PatientProfile profile = PatientProfile.create(
                req.displayName(),
                req.birthdate(),
                req.sex(),
                req.addressSido(),
                req.addressSigungu(),
                req.addressDong(),
                req.isPregnant(),
                req.consentDataShare()
        );
        profileRepository.save(profile);
        parentGridService.updateParentGrid(profile.getParentId());
        return ParentProfileResponse.from(profile, 0, 0);
    }

    // ─── 수정 ─────────────────────────────────────────────────────────────

    @Transactional
    public ParentProfileResponse updateProfile(UUID parentId, UpdateParentRequest req) {
        PatientProfile profile = findOrThrow(parentId);
        profile.update(
                req.displayName(),
                req.birthdate(),
                req.sex(),
                req.addressSido(),
                req.addressSigungu(),
                req.addressDong(),
                req.isPregnant(),
                req.consentDataShare()
        );
        parentGridService.updateParentGrid(parentId);
        int medicationCount = (int) medicationRepository.countByParentIdAndDeletedAtIsNull(parentId);
        int allergyCount = (int) allergyRepository.countByParentIdAndDeletedAtIsNull(parentId);
        return ParentProfileResponse.from(profile, medicationCount, allergyCount);
    }

    // ─── 삭제 ─────────────────────────────────────────────────────────────

    /**
     * 부모 프로파일 삭제.
     * 연결된 patient_allergies, device_tokens는 ON DELETE CASCADE로 함께 삭제됩니다.
     */
    @Transactional
    public void deleteProfile(UUID parentId) {
        PatientProfile profile = findOrThrow(parentId);
        profileRepository.delete(profile);
    }

    // ─── 내부 헬퍼 ────────────────────────────────────────────────────────

    public PatientProfile findOrThrow(UUID parentId) {
        return profileRepository.findById(parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARENT_NOT_FOUND));
    }

    /**
     * 동의 여부 확인 — 약장 조회 등 consent_data_share가 필요한 곳에서 호출.
     */
    public void requireConsent(UUID parentId) {
        PatientProfile profile = findOrThrow(parentId);
        if (!profile.isConsentDataShare()) {
            throw new CustomException(ErrorCode.CONSENT_REQUIRED);
        }
    }
}
