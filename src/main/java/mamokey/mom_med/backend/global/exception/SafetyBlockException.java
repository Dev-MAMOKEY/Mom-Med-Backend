package mamokey.mom_med.backend.global.exception;

import lombok.Getter;
import mamokey.mom_med.backend.safety.Verdict;

/**
 * 약물 안전 검사(DUR + NB)에서 BLOCK 판정 시 발생.
 * HTTP 409 Conflict 응답.
 */
@Getter
public class SafetyBlockException extends RuntimeException {

    private final Verdict verdict;

    public SafetyBlockException(Verdict verdict) {
        super("약물 안전 검사에서 차단되었습니다.");
        this.verdict = verdict;
    }
}
