package mamokey.mom_med.backend.domain.nb.controller;

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
public class NbDrugController {

	private final NbExtractionService nbExtractionService;

	public NbDrugController(NbExtractionService nbExtractionService) {
		this.nbExtractionService = nbExtractionService;
	}

	@PostMapping("/{itemSeq}/extract-nb")
	public ResponseEntity<NbExtractionTriggerResponse> extract(@PathVariable String itemSeq) {
		NbExtractionResult result = nbExtractionService.extract(itemSeq);
		return ResponseEntity.ok(NbExtractionTriggerResponse.from(result));
	}

	@GetMapping("/{itemSeq}/contraindications")
	public ResponseEntity<NbContraindicationsResponse> contraindications(@PathVariable String itemSeq) {
		NbExtractionResult result = nbExtractionService.findVerified(itemSeq);
		return ResponseEntity.ok(NbContraindicationsResponse.from(result));
	}
}
