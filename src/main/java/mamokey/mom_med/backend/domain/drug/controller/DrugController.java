package mamokey.mom_med.backend.domain.drug.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Drug", description = "약 검색 · 식별 (Slice 01)")
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
	@Operation(summary = "약 이름으로 품목기준코드(itemSeq) 식별",
	           description = "결과 1개: 200, 동명이품 여러 개: 300 (후보 목록), 없음: 404")
	@ApiResponses({
	        @ApiResponse(responseCode = "200", description = "단일 약 식별 성공"),
	        @ApiResponse(responseCode = "300", description = "동명이품 — 후보 목록 반환"),
	        @ApiResponse(responseCode = "404", description = "검색 결과 없음")
	})
	public ResponseEntity<?> identify(
	        @Parameter(description = "검색할 약 이름 (예: 타이레놀, 아스피린)", example = "타이레놀")
	        @RequestParam("name") @NotBlank String name) {
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
