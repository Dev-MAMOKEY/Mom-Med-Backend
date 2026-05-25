package mamokey.mom_med.backend.parent.dto;

import mamokey.mom_med.backend.parent.domain.PatientProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.UUID;

/**
 * GET /v1/parents/{parent_id} 응답 바디.
 *
 * age는 birthdate로부터 만 나이를 서버에서 계산합니다.
 * medication_count / allergy_count / condition_count는 응급카드·약 추가 UI에서
 * 별도 목록 API를 추가 호출하지 않아도 개수를 표시할 수 있도록 제공합니다.
 * condition_count는 Slice 05(기저질환) 구현 전까지 항상 0을 반환합니다.
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
        Short nx,
        Short ny,
        boolean isPregnant,
        boolean consentDataShare,
        LocalDateTime consentAt,
        int medicationCount,
        int allergyCount,
        int conditionCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ParentProfileResponse from(
            PatientProfile p,
            int medicationCount,
            int allergyCount
    ) {
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
                p.getNx(),
                p.getNy(),
                p.isPregnant(),
                p.isConsentDataShare(),
                p.getConsentAt(),
                medicationCount,
                allergyCount,
                0,  // conditionCount: Slice 05 구현 후 채워짐
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
