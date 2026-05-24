package mamokey.mom_med.backend.global.exception;

import lombok.Getter;

/**
 * 약물 안전 검사(DUR + NB)에서 BLOCK 판정 시 발생.
 * HTTP 409 Conflict 응답.
 *
 * <p>verdict는 Track B의 {@code Verdict} 또는 Track A의 {@code SafetyVerdict} 모두 허용합니다.
 * {@link mamokey.mom_med.backend.global.rsdata.BlockErrorResponse}가 Object로 직렬화합니다.</p>
 */
@Getter
public class SafetyBlockException extends RuntimeException {

    private final Object verdict;

    public SafetyBlockException(Object verdict) {
        super("약물 안전 검사에서 차단되었습니다.");
        this.verdict = verdict;
    }
}
