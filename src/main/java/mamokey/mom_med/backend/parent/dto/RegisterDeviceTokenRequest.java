package mamokey.mom_med.backend.parent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * POST /v1/parents/{parent_id}/device-tokens 요청 바디.
 * token은 서버에서 pgp_sym_encrypt로 암호화 후 저장됩니다.
 */
public record RegisterDeviceTokenRequest(

        @NotNull(message = "platform은 필수입니다.")
        @Pattern(regexp = "fcm|apns|web", message = "platform은 fcm, apns, web 중 하나여야 합니다.")
        String platform,

        @NotBlank(message = "token은 필수입니다.")
        String token
) {
}
