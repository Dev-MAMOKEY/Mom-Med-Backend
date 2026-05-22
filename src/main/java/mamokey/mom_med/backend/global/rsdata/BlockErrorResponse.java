package mamokey.mom_med.backend.global.rsdata;

/**
 * 약 추가 BLOCK 시 HTTP 409 응답 (프론트와 합의된 고정 구조).
 *
 * {
 *   "error": "block",
 *   "verdict": { "decision": "BLOCK", "evidences": [...] }
 * }
 *
 * verdict 타입은 Slice 03 완성 후 Verdict 타입으로 교체.
 */
public record BlockErrorResponse(
        String error,
        Object verdict
) {
    public static BlockErrorResponse of(Object verdict) {
        return new BlockErrorResponse("block", verdict);
    }
}
