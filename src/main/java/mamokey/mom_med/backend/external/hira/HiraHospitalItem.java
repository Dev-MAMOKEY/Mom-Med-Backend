package mamokey.mom_med.backend.external.hira;

import java.math.BigDecimal;

/**
 * HIRA 병원정보서비스(sno 11999) 응답 item.
 *
 * @param ykiho   요양기관기호 — HIRA API 전체에서 병원 식별 키
 * @param yadmNm  병원명
 * @param clCdNm  병원 종별 (상급종합, 종합병원, 의원 등)
 * @param addr    주소
 * @param telno   전화번호
 * @param xPos    경도
 * @param yPos    위도
 */
public record HiraHospitalItem(
        String ykiho,
        String yadmNm,
        String clCdNm,
        String addr,
        String telno,
        BigDecimal xPos,
        BigDecimal yPos
) {}
