package mamokey.mom_med.backend.parent.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * PATCH /v1/parents/{parent_id} 요청 바디.
 * 모든 필드가 선택사항 — null이면 기존 값 유지.
 */
public record UpdateParentRequest(

        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String displayName,

        LocalDate birthdate,

        @Pattern(regexp = "[MF]", message = "성별은 M 또는 F여야 합니다.")
        String sex,

        @Size(max = 50)
        String addressSido,

        @Size(max = 50)
        String addressSigungu,

        @Size(max = 50)
        String addressDong,

        Boolean isPregnant,

        Boolean consentDataShare,

        Short nx,   // 기상청 격자 X (region_grid ETL 없이 직접 지정할 때 사용)
        Short ny    // 기상청 격자 Y
) {
}
