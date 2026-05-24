package mamokey.mom_med.backend.parent.dto;

import mamokey.mom_med.backend.parent.domain.PatientAllergy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 알레르기 단일 항목 응답.
 */
public record AllergyResponse(
        Long id,
        UUID parentId,
        String allergenType,
        String allergenName,
        String allergenNorm,
        String severity,
        String notes,
        LocalDate confirmedAt,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {

    public static AllergyResponse from(PatientAllergy a) {
        return new AllergyResponse(
                a.getId(),
                a.getParentId(),
                a.getAllergenType(),
                a.getAllergenName(),
                a.getAllergenNorm(),
                a.getSeverity(),
                a.getNotes(),
                a.getConfirmedAt(),
                a.getCreatedAt(),
                a.getDeletedAt()
        );
    }
}
