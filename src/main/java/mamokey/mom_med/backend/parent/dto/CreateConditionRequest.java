package mamokey.mom_med.backend.parent.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /v1/parents/{parentId}/conditions 요청 바디.
 *
 * <p>conditionName과 kcdCode 중 하나는 반드시 제공해야 합니다.</p>
 * <ul>
 *   <li>kcdCode만 제공: HIRA API로 검증 후 공식 한글명 자동 사용</li>
 *   <li>conditionName만 제공: kcdCode 없이 자유 입력 저장</li>
 *   <li>둘 다 제공: kcdCode를 HIRA로 검증하되 conditionName은 입력값 그대로 사용</li>
 * </ul>
 */
public record CreateConditionRequest(

        /** 질환명 (선택 — kcdCode 있으면 HIRA 공식명으로 자동 채워짐) */
        @Size(max = 200, message = "질환명은 200자 이하여야 합니다.")
        String conditionName,

        /** HIRA KCD 코드 (선택, 예: "I10"). 제공 시 HIRA API로 유효성 검증. */
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
