package mamokey.mom_med.backend.domain.nb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import mamokey.mom_med.backend.domain.nb.dto.NbContraindicationsResponse;
import mamokey.mom_med.backend.domain.nb.dto.NbExtractionTriggerResponse;
import mamokey.mom_med.backend.domain.nb.service.NbExtractionResult;
import mamokey.mom_med.backend.domain.nb.service.NbExtractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NB 추출 결과를 확인하고 수동 트리거하는 약 도메인 Controller입니다.
 *
 * <p>실제 사용자 안전판정은 /v1/safety/check가 담당합니다. 이 Controller는 운영 smoke test와
 * 추출 결과 확인용으로, 특정 item_seq의 NB extraction 캐시를 만들거나 조회합니다.</p>
 */
@RestController
@RequestMapping("/v1/drugs")
@Tag(name = "NB", description = "NB 문서 추출 · 조회 (Slice 03)")
public class NbDrugController {

	private final NbExtractionService nbExtractionService;

	public NbDrugController(NbExtractionService nbExtractionService) {
		this.nbExtractionService = nbExtractionService;
	}

	@PostMapping("/{itemSeq}/extract-nb")
	@Operation(summary = "NB 문서 LLM 추출 트리거 (운영/smoke test용)",
	           description = "Gemini로 NB_DOC_DATA를 구조화 추출하고 DB에 캐싱합니다.")
	public ResponseEntity<NbExtractionTriggerResponse> extract(
	        @Parameter(description = "식약처 품목기준코드", example = "200611524")
	        @PathVariable String itemSeq) {
		NbExtractionResult result = nbExtractionService.extract(itemSeq);
		return ResponseEntity.ok(NbExtractionTriggerResponse.from(result));
	}

	@GetMapping("/{itemSeq}/contraindications")
	@Operation(summary = "검증된 NB 상호작용 목록 조회",
	           description = "verified=true인 캐시된 NB 추출 결과를 반환합니다. 캐시 없으면 404.")
	public ResponseEntity<NbContraindicationsResponse> contraindications(
	        @Parameter(description = "식약처 품목기준코드", example = "200611524")
	        @PathVariable String itemSeq) {
		NbExtractionResult result = nbExtractionService.findVerified(itemSeq);
		return ResponseEntity.ok(NbContraindicationsResponse.from(result));
	}
}
