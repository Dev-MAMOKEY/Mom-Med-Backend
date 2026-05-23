package mamokey.mom_med.backend.domain.safety.model;

/**
 * 약 추가 안전 판정 결과입니다.
 *
 * <p>Slice 02 MVP에서는 DUR 병용금기면 BLOCK, 노인주의면 WARN, 근거가 없으면 ALLOW입니다.
 * Slice 03 이후 NB/환자분류 금기가 추가되어도 같은 enum을 확장 없이 재사용합니다.</p>
 */
public enum SafetyDecision {
	BLOCK,
	WARN,
	ALLOW
}
