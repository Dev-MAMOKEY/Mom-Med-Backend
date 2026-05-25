package mamokey.mom_med.backend.parent.dto;

import jakarta.validation.constraints.Size;

/**
 * 병원 등록 요청.
 *
 * <p>ykiho 또는 yadmNm 중 하나는 반드시 제공해야 합니다.
 * ykiho가 있으면 HIRA 단건 조회 → 없으면 yadmNm으로 검색 후 첫 결과 사용.</p>
 *
 * @param ykiho    요양기관기호 (직접 지정 시)
 * @param yadmNm   병원명 검색어 (ykiho 없을 때 사용)
 * @param regular  단골 병원 여부
 * @param addedBy  등록자 (child | self)
 */
public record AddHospitalRequest(
        @Size(max = 500)
        String ykiho,

        @Size(max = 200)
        String yadmNm,

        boolean regular,

        @Size(max = 20)
        String addedBy
) {}
