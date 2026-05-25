package mamokey.mom_med.backend.parent.dto;

import jakarta.validation.constraints.Size;

/**
 * 약국 등록 요청.
 *
 * @param ykiho   요양기관기호 (직접 지정 시)
 * @param yadmNm  약국명 검색어 (ykiho 없을 때 사용)
 * @param regular 단골 약국 여부
 */
public record AddPharmacyRequest(
        @Size(max = 500)
        String ykiho,

        @Size(max = 200)
        String yadmNm,

        boolean regular
) {}
