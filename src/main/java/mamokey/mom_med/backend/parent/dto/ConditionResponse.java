package mamokey.mom_med.backend.parent.dto;

import mamokey.mom_med.backend.parent.domain.PatientCondition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 기저질환 단건 응답 DTO.
 */
public record ConditionResponse(
        Long id,
        UUID parentId,
        String conditionName,
        String conditionNorm,
        String kcdCode,
        String severity,
        LocalDate diagnosedAt,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {
    public static ConditionResponse from(PatientCondition c) {
        return new ConditionResponse(
                c.getId(),
                c.getParentId(),
                c.getConditionName(),
                c.getConditionNorm(),
                c.getKcdCode(),
                c.getSeverity(),
                c.getDiagnosedAt(),
                c.getNotes(),
                c.getCreatedAt(),
                c.getDeletedAt()
        );
    }
}
