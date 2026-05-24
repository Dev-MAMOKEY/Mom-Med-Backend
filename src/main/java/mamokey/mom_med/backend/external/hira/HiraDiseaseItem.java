package mamokey.mom_med.backend.external.hira;

/**
 * HIRA 질병정보서비스 API 응답 단건 item입니다.
 *
 * <p>sno 12904 getDissNameCodeList1 응답의 &lt;item&gt; 요소를 매핑합니다.</p>
 */
public record HiraDiseaseItem(
        /** KCD 질병 코드 (예: "I10", "E11") */
        String sickCd,
        /** 한글 질병명 (예: "본태성[원발성] 고혈압") */
        String sickNm,
        /** 영문 질병명 (예: "Essential (primary) hypertension") */
        String sickEngNm
) {
}
