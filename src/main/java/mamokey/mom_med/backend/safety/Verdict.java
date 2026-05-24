package mamokey.mom_med.backend.safety;

import java.util.List;

/**
 * 약물 안전 검사의 최종 판정 결과.
 *
 * <p>JSON 직렬화 시 프론트 합의 구조를 따릅니다:
 * {@code { "decision": "BLOCK", "evidences": [...] }}</p>
 *
 * <p>GlobalExceptionHandler → BlockErrorResponse.of(verdict)로 감싸져
 * {@code { "error": "block", "verdict": {...} }} 형태로 응답됩니다.</p>
 */
public record Verdict(
        Decision decision,
        List<Evidence> evidences
) {

    /** ALLOW 판정 (근거 없음) — MockSafetyJudgeService에서 사용 */
    public static Verdict allow() {
        return new Verdict(Decision.ALLOW, List.of());
    }

    /** 이 판정이 BLOCK인지 확인합니다. */
    public boolean isBlock() {
        return decision == Decision.BLOCK;
    }
}
