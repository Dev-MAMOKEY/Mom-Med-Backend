package mamokey.mom_med.backend.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 병원 등록 요청.
 *
 * <p>ykiho 또는 yadmNm 중 하나는 반드시 제공해야 합니다.
 * ykiho가 있으면 HIRA 단건 조회 → 없으면 yadmNm으로 검색 후 첫 결과 사용.</p>
 */
@Schema(description = "병원 등록 요청 — ykiho 또는 yadmNm 중 하나 필수")
public record AddHospitalRequest(

        @Schema(description = "요양기관기호 (HIRA 직접 지정 시 사용, 없으면 yadmNm 검색)", example = "B1100062")
        @Size(max = 500)
        String ykiho,

        @Schema(description = "병원명 검색어 (공식 기관명으로 검색 — 예: 세브란스병원, 서울대학교병원)", example = "세브란스병원")
        @Size(max = 200)
        String yadmNm,

        @Schema(description = "단골 병원 여부 — true면 목록 최상단 노출", example = "true")
        boolean regular,

        @Schema(description = "등록자 구분 (child | self)", example = "child", allowableValues = {"child", "self"})
        @Size(max = 20)
        String addedBy

) {}
