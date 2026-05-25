package mamokey.mom_med.backend.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비상연락처 등록/수정 요청")
public record CreateEmergencyContactRequest(

        @Schema(description = "이름", example = "홍길순")
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100)
        String name,

        @Schema(description = "관계 (배우자, 자녀, 형제 등)", example = "자녀")
        @NotBlank(message = "관계는 필수입니다.")
        @Size(max = 50)
        String relationship,

        @Schema(description = "연락처", example = "010-1234-5678")
        @NotBlank(message = "연락처는 필수입니다.")
        @Size(max = 50)
        String phone,

        @Schema(description = "표시 우선순위 — 낮을수록 먼저 노출 (기본값 0)", example = "0")
        int priority

) {}
