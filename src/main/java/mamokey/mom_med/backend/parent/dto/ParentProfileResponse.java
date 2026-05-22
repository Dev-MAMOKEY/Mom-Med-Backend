package mamokey.mom_med.backend.parent.dto;

import mamokey.mom_med.backend.parent.domain.PatientProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.UUID;

/**
 * GET /v1/parents/{parent_id} 응답 바디.
 * age는 birthdate로부터 만 나이를 서버에서 계산합니다.
 */
public record ParentProfileResponse(
        UUID parentId,
        String displayName,
        LocalDate birthdate,
        int age,
        String sex,
        String addressSido,
        String addressSigungu,
        String addressDong,
        boolean isPregnant,
        boolean consentDataShare,
        LocalDateTime consentAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ParentProfileResponse from(PatientProfile p) {
        int age = Period.between(p.getBirthdate(), LocalDate.now()).getYears();
        return new ParentProfileResponse(
                p.getParentId(),
                p.getDisplayName(),
                p.getBirthdate(),
                age,
                p.getSex(),
                p.getAddressSido(),
                p.getAddressSigungu(),
                p.getAddressDong(),
                p.isPregnant(),
                p.isConsentDataShare(),
                p.getConsentAt(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
