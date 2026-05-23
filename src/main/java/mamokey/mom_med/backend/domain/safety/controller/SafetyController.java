package mamokey.mom_med.backend.domain.safety.controller;

import jakarta.validation.Valid;
import mamokey.mom_med.backend.domain.safety.dto.SafetyCheckRequest;
import mamokey.mom_med.backend.domain.safety.model.SafetyDecision;
import mamokey.mom_med.backend.domain.safety.model.SafetyVerdict;
import mamokey.mom_med.backend.domain.safety.service.SafetyCheckService;
import mamokey.mom_med.backend.global.exception.SafetyBlockException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 약물 안전 검사 API Controller입니다.
 *
 * <p>현재 Slice 02에서는 DUR 병용금기와 노인주의만 검사합니다. Controller는 HTTP 상태코드 변환을
 * 담당하고, ITEM_SEQ 조회와 판정 로직은 {@link SafetyCheckService}에 위임합니다.</p>
 */
@RestController
@RequestMapping("/v1/safety")
public class SafetyController {

	private final SafetyCheckService safetyCheckService;

	public SafetyController(SafetyCheckService safetyCheckService) {
		this.safetyCheckService = safetyCheckService;
	}

	/**
	 * 부모의 기존 약 목록과 새 약 사이의 DUR 안전성을 검사합니다.
	 *
	 * <p>BLOCK은 프론트 합의 구조인 {@code {"error":"block","verdict":...}}로 내려야 하므로
	 * {@link SafetyBlockException}을 발생시켜 GlobalExceptionHandler가 HTTP 409로 변환하게 합니다.
	 * WARN/ALLOW는 정상 처리 가능한 결과라 HTTP 200을 반환합니다.</p>
	 */
	@PostMapping("/check")
	public ResponseEntity<SafetyVerdict> check(@Valid @RequestBody SafetyCheckRequest request) {
		SafetyVerdict verdict = safetyCheckService.check(request);
		if (verdict.decision() == SafetyDecision.BLOCK) {
			throw new SafetyBlockException(verdict);
		}
		return ResponseEntity.ok(verdict);
	}
}
