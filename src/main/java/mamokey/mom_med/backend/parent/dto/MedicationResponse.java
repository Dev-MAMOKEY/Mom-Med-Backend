package mamokey.mom_med.backend.parent.dto;

import mamokey.mom_med.backend.parent.domain.PatientMedication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 약장 단일 항목 응답.
 */
public record MedicationResponse(
        Long id,
        UUID parentId,
        String itemSeq,
        String drugName,
        String ingredientNorm,
        LocalDate startedOn,
        String memo,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {

    public static MedicationResponse from(PatientMedication m) {
        return new MedicationResponse(
                m.getId(),
                m.getParentId(),
                m.getItemSeq(),
                m.getDrugName(),
                m.getIngredientNorm(),
                m.getStartedOn(),
                m.getMemo(),
                m.getCreatedAt(),
                m.getDeletedAt()
        );
    }
}
