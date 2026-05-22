package mamokey.mom_med.backend.parent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /v1/parents 요청 바디.
 */
public record CreateParentRequest(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String displayName,

        @NotNull(message = "생년월일은 필수입니다.")
        LocalDate birthdate,

        @NotNull(message = "성별은 필수입니다.")
        @Pattern(regexp = "[MF]", message = "성별은 M 또는 F여야 합니다.")
        String sex,

        @Size(max = 50)
        String addressSido,

        @Size(max = 50)
        String addressSigungu,

        @Size(max = 50)
        String addressDong,

        boolean isPregnant,

        boolean consentDataShare
) {
}
