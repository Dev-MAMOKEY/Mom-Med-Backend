package mamokey.mom_med.backend.domain.safety.model;

import java.util.List;

/**
 * SafetyJudgeService가 반환하는 최종 판정 객체입니다.
 *
 * <p>Controller는 이 객체를 그대로 200 응답으로 내리거나, BLOCK일 때
 * {@code SafetyBlockException}에 담아 HTTP 409 응답으로 변환합니다.</p>
 */
public record SafetyVerdict(
		SafetyDecision decision,
		List<SafetyEvidence> evidences
) {
}
