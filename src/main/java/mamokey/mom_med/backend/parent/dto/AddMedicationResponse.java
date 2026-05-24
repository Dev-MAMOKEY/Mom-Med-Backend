package mamokey.mom_med.backend.parent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import mamokey.mom_med.backend.parent.domain.PatientMedication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * POST /v1/parents/{parentId}/medications 201 응답 바디.
 *
 * safety_check에 판정 결과와 근거를 포함합니다.
 * 목록 조회(GET)에서는 safety_check가 불필요하므로 MedicationResponse와 분리합니다.
 */
public record AddMedicationResponse(
        Long id,
        UUID parentId,
        String itemSeq,
        String drugName,
        String ingredientNorm,
        LocalDate startedOn,
        String memo,
        LocalDateTime createdAt,
        @JsonProperty("safety_check")
        SafetyVerdict safetyCheck
) {

    public static AddMedicationResponse from(PatientMedication m, SafetyVerdict verdict) {
        return new AddMedicationResponse(
                m.getId(),
                m.getParentId(),
                m.getItemSeq(),
                m.getDrugName(),
                m.getIngredientNorm(),
                m.getStartedOn(),
                m.getMemo(),
                m.getCreatedAt(),
                verdict
        );
    }
}
