package mamokey.mom_med.backend.parent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /v1/parents/{parent_id}/allergies 요청 바디.
 */
public record CreateAllergyRequest(

        @NotNull(message = "알레르기 유형은 필수입니다.")
        @Pattern(regexp = "drug|food|env|other", message = "allergen_type은 drug, food, env, other 중 하나여야 합니다.")
        String allergenType,

        @NotBlank(message = "알레르기 원인 물질 이름은 필수입니다.")
        @Size(max = 200)
        String allergenName,

        @Pattern(regexp = "severe|moderate|mild|unknown",
                message = "severity는 severe, moderate, mild, unknown 중 하나여야 합니다.")
        String severity,

        String notes,

        LocalDate confirmedAt
) {
}
