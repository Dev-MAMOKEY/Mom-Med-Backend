package mamokey.mom_med.backend.domain.drug.controller;

import jakarta.validation.constraints.NotBlank;
import mamokey.mom_med.backend.domain.drug.service.DrugIdentifyResult;
import mamokey.mom_med.backend.domain.drug.service.DrugIdentifyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 약 식별 API를 외부에 노출하는 Controller입니다.
 *
 * <p>Controller는 HTTP 요청/응답 변환만 담당하고, 식약처 API 호출과 캐시 갱신 판단은
 * {@link DrugIdentifyService}에 위임합니다.</p>
 */
@Validated
@RestController
@RequestMapping("/v1/drugs")
public class DrugController {

	private final DrugIdentifyService drugIdentifyService;

	public DrugController(DrugIdentifyService drugIdentifyService) {
		this.drugIdentifyService = drugIdentifyService;
	}

	/**
	 * 사용자가 입력한 약 이름을 식약처 ITEM_SEQ 기준으로 식별합니다.
	 *
	 * <p>결과가 하나로 확정되면 200, 후보가 여러 개면 300, 검색 결과가 없으면 404를 반환합니다.</p>
	 */
	@GetMapping("/identify")
	public ResponseEntity<?> identify(@RequestParam("name") @NotBlank String name) {
		return switch (drugIdentifyService.identify(name)) {
			case DrugIdentifyResult.Identified identified -> ResponseEntity.ok(identified.response());
			case DrugIdentifyResult.Candidates candidates -> ResponseEntity
					.status(HttpStatus.MULTIPLE_CHOICES)
					.body(candidates.response());
			case DrugIdentifyResult.NotFound notFound -> ResponseEntity
					.status(HttpStatus.NOT_FOUND)
					.body(notFound.response());
		};
	}
}
