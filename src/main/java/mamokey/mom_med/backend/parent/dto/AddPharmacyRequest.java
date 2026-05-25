package mamokey.mom_med.backend.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 약국 등록 요청.
 *
 * <p>ykiho 또는 yadmNm 중 하나는 반드시 제공해야 합니다.</p>
 */
@Schema(description = "약국 등록 요청 — ykiho 또는 yadmNm 중 하나 필수")
public record AddPharmacyRequest(

        @Schema(description = "요양기관기호 (HIRA 직접 지정 시 사용)", example = "B1234567")
        @Size(max = 500)
        String ykiho,

        @Schema(description = "약국명 검색어 (공식 기관명 — 예: 온누리약국)", example = "온누리약국")
        @Size(max = 200)
        String yadmNm,

        @Schema(description = "단골 약국 여부 — true면 목록 최상단 노출", example = "true")
        boolean regular

) {}
