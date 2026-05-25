package mamokey.mom_med.backend.external.hira;

import java.math.BigDecimal;

/**
 * HIRA 약국정보서비스(sno 12100) 응답 item.
 *
 * @param ykiho  요양기관기호
 * @param yadmNm 약국명
 * @param addr   주소
 * @param telno  전화번호
 * @param xPos   경도
 * @param yPos   위도
 */
public record HiraPharmacyItem(
        String ykiho,
        String yadmNm,
        String addr,
        String telno,
        BigDecimal xPos,
        BigDecimal yPos
) {}
