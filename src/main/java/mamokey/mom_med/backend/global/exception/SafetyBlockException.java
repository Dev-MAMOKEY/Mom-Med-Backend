package mamokey.mom_med.backend.global.exception;

import lombok.Getter;

/**
 * 약물 안전 검사(DUR + NB)에서 BLOCK 판정 시 발생.
 * HTTP 409 Conflict 응답.
 *
 * verdict 타입은 Slice 03 SafetyJudgeService 완성 후 Verdict 타입으로 교체.
 */
@Getter
public class SafetyBlockException extends RuntimeException {

    private final Object verdict;

    public SafetyBlockException(Object verdict) {
        super("약물 안전 검사에서 차단되었습니다.");
        this.verdict = verdict;
    }
}
