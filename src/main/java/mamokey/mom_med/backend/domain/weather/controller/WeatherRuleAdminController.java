package mamokey.mom_med.backend.domain.weather.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mamokey.mom_med.backend.domain.weather.dto.WeatherRuleAdminListResponse;
import mamokey.mom_med.backend.domain.weather.dto.WeatherRuleAdminResponse;
import mamokey.mom_med.backend.domain.weather.dto.WeatherRuleApproveRequest;
import mamokey.mom_med.backend.domain.weather.service.WeatherRuleAdminService;
import mamokey.mom_med.backend.global.rsdata.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 날씨 룰 의료진 검수 API입니다.
 */
@RestController
@RequestMapping("/v1/admin/weather-rules")
@Tag(name = "Weather Rule Admin", description = "날씨 건강 룰 의료진 검수 (Slice 07)")
public class WeatherRuleAdminController {

	private final WeatherRuleAdminService weatherRuleAdminService;

	public WeatherRuleAdminController(WeatherRuleAdminService weatherRuleAdminService) {
		this.weatherRuleAdminService = weatherRuleAdminService;
	}

	@GetMapping
	@Operation(
			summary = "날씨 룰 목록 조회 (검수 큐)",
			description = """
					`ref.weather_rules` 전체 목록을 반환합니다.
					- `status=needs_review`: `general_knowledge_used=true` && `approved_at IS NULL` 룰만 필터링합니다.
					- `status` 미지정 시 전체 룰을 반환합니다.
					- `requires_review=true` 룰은 의료진 승인 전까지 ETL 푸시 대상에서 제외됩니다.
					"""
	)
	public ResponseEntity<RsData<WeatherRuleAdminListResponse>> getRules(
			@Parameter(description = "필터 조건. `needs_review` 지정 시 미승인 룰만 반환.")
			@RequestParam(required = false) String status
	) {
		return ResponseEntity.ok(RsData.ok(weatherRuleAdminService.getRules(status)));
	}

	@PostMapping("/{ruleId}/approve")
	@Operation(
			summary = "날씨 룰 의료진 승인",
			description = """
					`approved_by` + `approved_at`을 기록하고 `requires_review` 상태를 해제합니다.
					- `adjusted_message` 지정 시 seed 메시지 대신 승인자가 보정한 문구로 저장합니다.
					- 이미 승인된 룰에 재요청하면 승인자·시각·메시지를 덮어씁니다.
					"""
	)
	public ResponseEntity<RsData<WeatherRuleAdminResponse>> approve(
			@PathVariable Long ruleId,
			@Valid @RequestBody WeatherRuleApproveRequest request
	) {
		return ResponseEntity.ok(RsData.ok(weatherRuleAdminService.approve(ruleId, request)));
	}
}
