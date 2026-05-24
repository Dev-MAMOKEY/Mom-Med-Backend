package mamokey.mom_med.backend.safety;

/**
 * 약물 안전 검사 판정 결과.
 *
 * <p>BLOCK: 즉각 차단 (HTTP 409). 복용 불가.
 * WARN: 경고 있음 (HTTP 201). 의사 상담 권고.
 * INFO: 참고 정보 있음 (HTTP 201). 일반 안내.
 * ALLOW: 이상 없음 (HTTP 201). 약장 추가 허용.</p>
 */
public enum Decision {
    BLOCK,
    WARN,
    INFO,
    ALLOW
}
