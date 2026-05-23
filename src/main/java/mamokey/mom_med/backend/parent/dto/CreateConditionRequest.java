package mamokey.mom_med.backend.parent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /v1/parents/{parentId}/conditions 요청 바디.
 *
 * <p>kcd_code는 선택사항으로, 프론트에서 HIRA 질병코드 검색 API를 통해
 * 확인된 코드를 전달하면 저장됩니다. 없으면 null로 저장됩니다.</p>
 */
public record CreateConditionRequest(

        @NotBlank(message = "질환명은 필수입니다.")
        @Size(max = 200, message = "질환명은 200자 이하여야 합니다.")
        String conditionName,

        /** HIRA KCD 코드 (선택, 예: "I10") */
        @Size(max = 20)
        String kcdCode,

        @Pattern(regexp = "severe|moderate|mild|unknown",
                message = "severity는 severe, moderate, mild, unknown 중 하나여야 합니다.")
        String severity,

        LocalDate diagnosedAt,

        @Size(max = 500)
        String notes
) {
}
